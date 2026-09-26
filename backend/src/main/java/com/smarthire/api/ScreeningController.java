package com.smarthire.api;

import com.smarthire.domain.*;
import com.smarthire.repository.*;
import com.smarthire.security.JwtService;
import com.smarthire.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ScreeningController {

    /** Row of the per-JD ranked shortlist (Admin dashboard). */
    public record ShortlistRow(Long applicationId, String candidateName, String profileType,
                               Double resumeScore, Double resumeConfidence,
                               Double qaScore, Double combinedScore,
                               Band band, String status) {}

    public record BatchAllResult(int jdsProcessed, List<ScreeningService.BatchResult> results) {}

    private final CandidateRepository candidateRepo;
    private final ApplicationRepository appRepo;
    private final ScreeningService screeningService;
    private final JdRepository jdRepo;
    private final AuditService audit;

    public ScreeningController(CandidateRepository candidateRepo, ApplicationRepository appRepo,
                               ScreeningService screeningService, JdRepository jdRepo, AuditService audit) {
        this.candidateRepo = candidateRepo;
        this.appRepo = appRepo;
        this.screeningService = screeningService;
        this.jdRepo = jdRepo;
        this.audit = audit;
    }

    public record CandidateView(Long id, String name, String email, Long appliedJd,
                                int experienceYears, String status) {}

    @GetMapping("/candidates")
    @PreAuthorize("hasRole('ADMIN')")
    public List<CandidateView> candidates() {
        return candidateRepo.findAll().stream()
                .map(c -> new CandidateView(c.getId(), c.getName(), c.getEmail(), c.getAppliedJd(),
                        c.getExperienceYears(), c.getStatus()))
                .toList();
    }

    @PostMapping("/jds/{id}/resume-score")
    @PreAuthorize("hasRole('ADMIN')")
    public ScreeningService.BatchResult runScreening(@PathVariable Long id) {
        return screeningService.screenJd(id);
    }

    /** §9+ : ranked shortlist per JD — the Admin dashboard table. */
    @GetMapping("/jds/{id}/applications")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ShortlistRow> shortlist(@PathVariable Long id) {
        return appRepo.findByJdId(id).stream()
                .map(a -> {
                    Candidate c = candidateRepo.findById(a.getCandidateId()).orElse(null);
                    return new ShortlistRow(a.getId(),
                            c == null ? "unknown" : c.getName(),
                            c == null ? null : c.getProfileType(),
                            a.getResumeScore(), a.getResumeConfidence(),
                            a.getQaScore(), a.getCombinedScore(),
                            a.getBand(), a.getStatus().name());
                })
                .toList();
    }

    /** Batch auto-scoring across ALL JDs (manual trigger; a scheduler wraps the same call). */
    @PostMapping("/jds/resume-score-all")
    @PreAuthorize("hasRole('ADMIN')")
    public BatchAllResult scoreAll() {
        List<ScreeningService.BatchResult> results = new ArrayList<>();
        for (JobDescription jd : jdRepo.findAll()) {
            results.add(screeningService.screenJd(jd.getId()));
        }
        audit.record("RUN_BATCH_SCORING", "JD", null, "batch across " + results.size() + " JDs");
        return new BatchAllResult(results.size(), results);
    }

    static JwtService.Principal principal(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof JwtService.Principal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
