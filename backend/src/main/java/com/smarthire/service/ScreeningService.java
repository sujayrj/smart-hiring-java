package com.smarthire.service;

import com.smarthire.ai.ControlledLlmException;
import com.smarthire.ai.LlmClient;
import com.smarthire.domain.*;
import com.smarthire.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Résumé screening (spec §7.1): Admin triggers per-JD batch; each candidate's
 * résumé + JD goes to resume_match; validated results are persisted BEFORE
 * status updates (§13); threshold promotes to SCREENING or archives with reason.
 */
@Service
public class ScreeningService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningService.class);

    private final JdRepository jdRepo;
    private final CandidateRepository candidateRepo;
    private final ApplicationRepository appRepo;
    private final LlmClient llm;
    private final AuditService audit;

    public ScreeningService(JdRepository jdRepo, CandidateRepository candidateRepo,
                            ApplicationRepository appRepo, LlmClient llm, AuditService audit) {
        this.jdRepo = jdRepo;
        this.candidateRepo = candidateRepo;
        this.appRepo = appRepo;
        this.llm = llm;
        this.audit = audit;
    }

    public record BatchRow(Long candidateId, String candidateName, Double score, Double confidence,
                           List<String> matchedSkills, List<String> gaps, String summary,
                           String outcome, String reason) {}

    public record BatchResult(Long jdId, int scored, int promoted, int archived, int failed, List<BatchRow> rows) {}

    @Transactional
    public BatchResult screenJd(Long jdId) {
        JobDescription jd = jdRepo.findById(jdId)
                .orElseThrow(() -> new IllegalArgumentException("JD not found: " + jdId));
        String jdText = jd.getTitle() + " | must-have: " + jd.getMustHave()
                + " | nice-to-have: " + jd.getNiceToHave() + " | " + jd.getSummary();

        List<Candidate> candidates = candidateRepo.findByAppliedJd(jdId);
        List<BatchRow> rows = new ArrayList<>();
        int promoted = 0, archived = 0, failed = 0;

        for (Candidate c : candidates) {
            try {
                LlmClient.ResumeMatchResult r = llm.resumeMatch(jdText, c.getResume());

                Application app = appRepo.findByCandidateIdAndJdId(c.getId(), jdId)
                        .orElseGet(() -> {
                            Application a = new Application();
                            a.setCandidateId(c.getId());
                            a.setJdId(jdId);
                            return a;
                        });

                // persist scoring FIRST (§13), then status — same transaction
                app.setResumeScore(r.score());
                app.setResumeConfidence(r.confidence());
                app.setResumeMatched(String.join(", ", r.matchedSkills()));
                app.setResumeGaps(String.join(", ", r.gaps()));
                app.setResumeSummary(r.summary());
                app.setStatus(ApplicationStatus.RESUME_SCORED);
                app = appRepo.save(app);

                if (r.score() >= jd.getPassThreshold()) {
                    app.setStatus(ApplicationStatus.SCREENING);
                    app.setNextSteps("Complete the timed Q&A screening");
                    c.setStatus("SCREENING");
                    promoted++;
                    rows.add(new BatchRow(c.getId(), c.getName(), r.score(), r.confidence(),
                            r.matchedSkills(), r.gaps(), r.summary(), "SCREENING", null));
                } else {
                    app.setStatus(ApplicationStatus.ARCHIVED);
                    app.setNextSteps("Résumé score below threshold " + jd.getPassThreshold());
                    c.setStatus("ARCHIVED");
                    archived++;
                    rows.add(new BatchRow(c.getId(), c.getName(), r.score(), r.confidence(),
                            r.matchedSkills(), r.gaps(), r.summary(), "ARCHIVED",
                            "Score " + r.score() + " below pass threshold " + jd.getPassThreshold()));
                }
                appRepo.save(app);
                candidateRepo.save(c);
            } catch (ControlledLlmException e) {
                failed++;
                log.warn("Screening failed for candidate {} on JD {}: {}", c.getId(), jdId, e.getMessage());
                rows.add(new BatchRow(c.getId(), c.getName(), null, null, List.of(), List.of(),
                        null, "FAILED", "LLM returned invalid output after one retry"));
            }
        }

        audit.record("RUN_RESUME_SCREENING", "JD", jdId,
                "scored=" + candidates.size() + " promoted=" + promoted + " archived=" + archived + " failed=" + failed);
        return new BatchResult(jdId, candidates.size(), promoted, archived, failed, rows);
    }
}
