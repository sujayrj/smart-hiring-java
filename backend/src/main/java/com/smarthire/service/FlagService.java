package com.smarthire.service;

import com.smarthire.domain.*;
import com.smarthire.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Flags module (§12): Admin-only flags from anti-cheat telemetry, low aggregate
 * confidence (< 0.6) and résumé/Q&A divergence (> 30). Evidence for human review,
 * never automatic rejection.
 */
@Service
public class FlagService {

    public record FlagView(Long applicationId, String candidateName, String type, String detail) {}

    private final AnticheatRepository anticheatRepo;
    private final ApplicationRepository appRepo;
    private final CandidateRepository candidateRepo;

    public FlagService(AnticheatRepository anticheatRepo, ApplicationRepository appRepo,
                       CandidateRepository candidateRepo) {
        this.anticheatRepo = anticheatRepo;
        this.appRepo = appRepo;
        this.candidateRepo = candidateRepo;
    }

    public List<FlagView> computeFlags() {
        List<FlagView> flags = new java.util.ArrayList<>();
        for (Application app : appRepo.findAll()) {
            flags.addAll(flagsFor(app));
        }
        return flags;
    }

    /** Per-application flags — reused by the Admin drill-down view. */
    public List<FlagView> flagsFor(Application app) {
        List<FlagView> flags = new java.util.ArrayList<>();
        if (app.getStatus() == ApplicationStatus.APPLIED || app.getStatus() == ApplicationStatus.RESUME_SCORED) {
            return flags;
        }
        String name = candidateRepo.findById(app.getCandidateId()).map(Candidate::getName).orElse("candidate#" + app.getCandidateId());

        // §12: aggregate confidence below 0.6 → Admin-only flag
        if (app.getResumeConfidence() != null && app.getResumeConfidence() < 0.6) {
            flags.add(new FlagView(app.getId(), name, "LOW_CONFIDENCE",
                    "resume confidence " + app.getResumeConfidence() + " < 0.6"));
        }

        // §12: résumé vs Q&A divergence > 30 → Admin-only flag
        if (app.getResumeScore() != null && app.getQaScore() != null) {
            double divergence = Math.abs(app.getResumeScore() - app.getQaScore());
            if (divergence > 30) {
                flags.add(new FlagView(app.getId(), name, "SCORE_DIVERGENCE",
                        "resume " + Math.round(app.getResumeScore()) + " vs Q&A-norm "
                                + Math.round(app.getQaScore()) + " → Δ " + Math.round(divergence)));
            }
        }

        // §12: browser telemetry (tab-switch / paste / copy-question) → Admin-only flag
        long events = anticheatRepo.findByApplicationId(app.getId()).size();
        if (events > 0) {
            flags.add(new FlagView(app.getId(), name, "ANTICHEAT_TELEMETRY",
                    events + " suspicious event(s) recorded"));
        }
        return flags;
    }

    @Transactional
    public void recordEvent(Long applicationId, String eventType, String payload) {
        AnticheatEvent e = new AnticheatEvent();
        e.setApplicationId(applicationId);
        e.setEventType(eventType);
        e.setPayload(payload);
        anticheatRepo.save(e);
    }
}
