package com.smarthire.api;

import com.smarthire.domain.*;
import com.smarthire.repository.*;
import com.smarthire.security.JwtService;
import com.smarthire.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/applications/{id}")
public class ApplicationController {

    /** Candidate-facing aggregate view — NO AI evidence (§8.3, §11). */
    public record ApplicationView(Long id, Long jdId, String status, Band band,
                                  String nextSteps, String interviewerName, String interviewAt) {}

    public record SubmitAnswerRequest(@NotNull Long questionId, @NotBlank String answerText,
                                      boolean timeout, Integer timeTakenSeconds) {}

    public record AssignRequest(@NotNull Long interviewerId, @NotNull String scheduledAt) {}

    public record DecisionRequest(@NotNull Decision decision) {}

    public record NoteRequest(@NotBlank String text) {}

    public record OverrideBandRequest(@NotNull Band band, String nextSteps) {}

    /** Admin/Interviewer drill-down — full evidence with justifications (authorized roles only). */
    public record DetailDto(Long applicationId, String candidateName, String candidateEmail,
                            String jdTitle, String status, Band band, Double combinedScore, String nextSteps,
                            Double resumeScore, Double resumeConfidence,
                            String resumeMatched, String resumeGaps, String resumeSummary,
                            List<QaRow> qa, List<FlagService.FlagView> flags, InterviewBlock interview) {}

    public record QaRow(Long questionId, String questionText, String answerText,
                        Double score, Double confidence, List<String> rubricHits,
                        Integer timeTakenSeconds, boolean timeout) {}

    public record InterviewBlock(String interviewerName, String scheduledAt, String status,
                                 String decision, List<Map<String, Object>> notes) {}

    /** Candidate breakdown — per-question scores WITHOUT justifications/confidence (hidden). */
    public record MyResultDto(Long applicationId, String jdTitle, Band band, Double combinedScore,
                              List<Row> breakdown) {}

    public record Row(String questionText, Double score, Integer timeTakenSeconds, boolean timeout) {}

    private final ApplicationRepository appRepo;
    private final QaService qaService;
    private final InterviewService interviewService;
    private final AccessService accessService;
    private final AuditService audit;
    private final FlagService flagService;
    private final UserRepository userRepo;
    private final CandidateRepository candidateRepo;
    private final JdRepository jdRepo;
    private final QuestionRepository questionRepo;
    private final AnswerRepository answerRepo;

    public ApplicationController(ApplicationRepository appRepo, QaService qaService,
                                 InterviewService interviewService, AccessService accessService,
                                 AuditService audit, FlagService flagService,
                                 UserRepository userRepo, CandidateRepository candidateRepo,
                                 JdRepository jdRepo, QuestionRepository questionRepo,
                                 AnswerRepository answerRepo) {
        this.appRepo = appRepo;
        this.qaService = qaService;
        this.interviewService = interviewService;
        this.accessService = accessService;
        this.audit = audit;
        this.flagService = flagService;
        this.userRepo = userRepo;
        this.candidateRepo = candidateRepo;
        this.jdRepo = jdRepo;
        this.questionRepo = questionRepo;
        this.answerRepo = answerRepo;
    }

    // ---- §9: GET /applications/{id} — status + aggregate result ----
    @GetMapping
    public ApplicationView get(@PathVariable Long id, Authentication auth) {
        accessService.assertCanViewApplication(id, principal(auth));
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return toView(app);
    }

    // ---- Admin / assigned-interviewer drill-down: FULL evidence ----
    // (UseCase line 104/123: Admin + assigned Interviewer only; lines 117/172/231:
    //  candidates must never see justifications, rubric hits or internal evidence)
    @GetMapping("/detail")
    public DetailDto detail(@PathVariable Long id, Authentication auth) {
        accessService.assertCanViewFullEvidence(id, principal(auth));
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Candidate c = candidateRepo.findById(app.getCandidateId()).orElse(null);
        JobDescription jd = jdRepo.findById(app.getJdId()).orElse(null);

        List<QaRow> qa = new ArrayList<>();
        for (Answer a : answerRepo.findByApplicationId(id)) {
            Question q = questionRepo.findById(a.getQuestionId()).orElse(null);
            qa.add(new QaRow(a.getQuestionId(),
                    q == null ? "question#" + a.getQuestionId() : q.getText(),
                    a.getAnswerText(), a.getScore(), a.getConfidence(),
                    a.getRubricHits(), a.getTimeTakenSeconds(), a.isTimeoutSubmit()));
        }

        InterviewBlock block = null;
        var optInt = interviewRepo().findByApplicationId(id);
        if (optInt.isPresent()) {
            Interview i = optInt.get();
            String iname = userRepo.findById(i.getInterviewerId()).map(User::getUsername).orElse(null);
            List<Map<String, Object>> notes = new ArrayList<>();
            for (Interview.Note n : i.getNotes()) {
                notes.add(Map.of("authorRole", n.getAuthorRole(), "text", n.getText(),
                        "createdAt", n.getCreatedAt().toString()));
            }
            block = new InterviewBlock(iname, i.getScheduledAt().toString(),
                    i.getStatus().name(),
                    i.getDecision() == null ? null : i.getDecision().name(), notes);
        }

        return new DetailDto(app.getId(),
                c == null ? "unknown" : c.getName(),
                c == null ? null : c.getEmail(),
                jd == null ? null : jd.getTitle(),
                app.getStatus().name(), app.getBand(), app.getCombinedScore(), app.getNextSteps(),
                app.getResumeScore(), app.getResumeConfidence(),
                app.getResumeMatched(), app.getResumeGaps(), app.getResumeSummary(),
                qa, flagService.flagsFor(app), block);
    }

    // ---- Candidate breakdown: aggregate + per-question, justifications hidden ----
    @GetMapping("/my-result")
    public MyResultDto myResult(@PathVariable Long id, Authentication auth) {
        accessService.assertCandidateOwner(id, principal(auth));
        Application app = appRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        JobDescription jd = jdRepo.findById(app.getJdId()).orElse(null);

        List<Row> rows = new ArrayList<>();
        for (Answer a : answerRepo.findByApplicationId(id)) {
            Question q = questionRepo.findById(a.getQuestionId()).orElse(null);
            rows.add(new Row(q == null ? "question#" + a.getQuestionId() : q.getText(),
                    a.getScore(), a.getTimeTakenSeconds(), a.isTimeoutSubmit()));
        }
        return new MyResultDto(app.getId(), jd == null ? null : jd.getTitle(),
                app.getBand(), app.getCombinedScore(), rows);
    }

    // ---- §9: GET /applications/{id}/questions — Candidate ----
    @GetMapping("/questions")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<QaService.QuestionView> questions(@PathVariable Long id, Authentication auth) {
        accessService.assertCanViewApplication(id, principal(auth));
        return qaService.getQuestions(id);
    }

    // ---- §9: POST /applications/{id}/answers — Candidate ----
    @PostMapping("/answers")
    @PreAuthorize("hasRole('CANDIDATE')")
    public QaService.AnswerAck submit(@PathVariable Long id, @Valid @RequestBody SubmitAnswerRequest req,
                                      Authentication auth) {
        accessService.assertCanViewApplication(id, principal(auth));
        return qaService.submitAnswer(id, req.questionId(), req.answerText(), req.timeout(),
                req.timeTakenSeconds());
    }

    // ---- §9: POST /applications/{id}/assign-interviewer — Admin ----
    @PostMapping("/assign-interviewer")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Interview assign(@PathVariable Long id, @Valid @RequestBody AssignRequest req) {
        return interviewService.assign(id, req.interviewerId(), Instant.parse(req.scheduledAt()));
    }

    // ---- §9: POST /applications/{id}/decision — Interviewer ----
    @PostMapping("/decision")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public Interview decision(@PathVariable Long id, @Valid @RequestBody DecisionRequest req,
                              Authentication auth) {
        AccessService.Principal p = principal(auth);
        return interviewService.recordDecision(id, req.decision(),
                new InterviewService.JwtPrincipal(p.userId(), p.username(), p.role()));
    }

    // ---- §9: POST /applications/{id}/notes — Interviewer/Admin ----
    @PostMapping("/notes")
    @PreAuthorize("hasAnyRole('INTERVIEWER','ADMIN')")
    public Interview note(@PathVariable Long id, @Valid @RequestBody NoteRequest req,
                          Authentication auth) {
        AccessService.Principal p = principal(auth);
        return interviewService.addNote(id, req.text(), p.role());
    }

    // ---- Admin reviews the LLM band → may override (human final call) ----
    @PostMapping("/override-band")
    @PreAuthorize("hasRole('ADMIN')")
    public ApplicationView overrideBand(@PathVariable Long id,
                                        @Valid @RequestBody OverrideBandRequest req,
                                        Authentication auth) {
        accessService.assertCanViewApplication(id, principal(auth));
        Application app = interviewService.overrideBand(id, req.band(), req.nextSteps());
        return toView(app);
    }

    // ---- Stubbed invitation: toast + audit event, no real email ----
    @PostMapping("/invite")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> invite(@PathVariable Long id) {
        return Map.of("message", interviewService.invite(id));
    }

    private ApplicationView toView(Application app) {
        String interviewerName = null;
        String interviewAt = null;
        var optInt = interviewRepo().findByApplicationId(app.getId());
        if (optInt.isPresent()) {
            Interview i = optInt.get();
            interviewerName = userRepo.findById(i.getInterviewerId())
                    .map(User::getUsername).orElse(null);
            interviewAt = i.getScheduledAt().toString();
        }
        return new ApplicationView(app.getId(), app.getJdId(), app.getStatus().name(),
                app.getBand(), app.getNextSteps(), interviewerName, interviewAt);
    }

    private com.smarthire.repository.InterviewRepository interviewRepo() {
        return interviewService.repo();
    }

    static AccessService.Principal principal(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof JwtService.Principal p) {
            return new AccessService.Principal(p.userId(), p.username(), p.role());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
