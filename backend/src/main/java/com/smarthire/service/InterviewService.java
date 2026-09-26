package com.smarthire.service;

import com.smarthire.domain.*;
import com.smarthire.repository.CandidateRepository;
import com.smarthire.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Interview module (§7.4): Admin assigns interviewer+date; interviewer sees only
 * assigned candidates; records decision + timestamped notes; audit on state changes.
 */
@Service
public class InterviewService {

    private final InterviewRepository interviewRepo;
    private final ApplicationRepository appRepo;
    private final UserRepository userRepo;
    private final CandidateRepository candidateRepo;
    private final AuditService audit;

    public InterviewService(InterviewRepository interviewRepo, ApplicationRepository appRepo,
                            UserRepository userRepo, CandidateRepository candidateRepo, AuditService audit) {
        this.interviewRepo = interviewRepo;
        this.appRepo = appRepo;
        this.userRepo = userRepo;
        this.candidateRepo = candidateRepo;
        this.audit = audit;
    }

    @Transactional
    public Interview assign(Long applicationId, Long interviewerId, Instant scheduledAt) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Interview interview = interviewRepo.findByApplicationId(applicationId).orElseGet(Interview::new);
        interview.setApplicationId(applicationId);
        interview.setInterviewerId(interviewerId);
        interview.setScheduledAt(scheduledAt);
        interview.setStatus(InterviewStatus.SCHEDULED);
        interview = interviewRepo.save(interview);

        app.setStatus(ApplicationStatus.INTERVIEW_SCHEDULED);
        app.setNextSteps("Interview scheduled");
        appRepo.save(app);
        audit.record("ASSIGN_INTERVIEWER", "Application", applicationId,
                "interviewer=" + interviewerId + " at=" + scheduledAt);
        return interview;
    }

    public List<Interview> assignedTo(Long interviewerId) {
        return interviewRepo.findByInterviewerId(interviewerId);
    }

    /** Interviewer isolation: only the assigned interviewer (or Admin) may access. */
    public void assertCanAccess(Interview interview, JwtPrincipal principal) {
        if (principal.role().equals("ADMIN")) return;
        if (principal.role().equals("INTERVIEWER") && interview.getInterviewerId().equals(principal.userId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your assigned candidate");
    }

    public record JwtPrincipal(Long userId, String username, String role) {}

    @Transactional
    public Interview recordDecision(Long applicationId, Decision decision, JwtPrincipal principal) {
        Interview interview = interviewRepo.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No interview assigned"));
        assertCanAccess(interview, new JwtPrincipal(principal.userId(), principal.username(), principal.role()));

        interview.setDecision(decision);
        interview.setStatus(InterviewStatus.COMPLETED);
        interview = interviewRepo.save(interview);

        Application app = appRepo.findById(applicationId).orElse(null);
        if (app != null) {
            app.setStatus(ApplicationStatus.INTERVIEW_DONE);
            app.setNextSteps(switch (decision) {
                case ACCEPTED -> "Accepted — offer process begins";
                case REJECTED -> "Application closed after interview";
                case ON_HOLD -> "On hold pending further review";
                case NO_SHOW -> "Candidate did not attend the interview";
            });
            appRepo.save(app);
        }
        audit.record("RECORD_DECISION", "Application", applicationId, "decision=" + decision);
        return interview;
    }

    /** Admin reviews the LLM band and may override it — human final call (Advanced task). */
    @Transactional
    public Application overrideBand(Long applicationId, Band band, String nextSteps) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Band previous = app.getBand();
        app.setBand(band);
        if (nextSteps != null && !nextSteps.isBlank()) {
            app.setNextSteps(nextSteps);
        }
        app = appRepo.save(app);
        audit.record("OVERRIDE_BAND", "Application", applicationId,
                (previous == null ? "none" : previous) + " → " + band);
        return app;
    }

    /** Stubbed invitation: toast + audit event only — no real email (guardrail). */
    public String invite(Long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Candidate candidate = candidateRepo.findById(app.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate not found"));
        audit.record("INVITE_SENT", "Application", applicationId,
                "stubbed invitation to " + candidate.getEmail());
        return "Invitation (stubbed) triggered for " + candidate.getName() + " <" + candidate.getEmail() + ">";
    }

    @Transactional
    public Interview addNote(Long applicationId, String text, String authorRole) {
        Interview interview = interviewRepo.findByApplicationId(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No interview assigned"));
        Interview.Note note = new Interview.Note();
        note.setAuthorRole(authorRole);
        note.setText(text);
        note.setCreatedAt(Instant.now());
        interview.getNotes().add(note);
        interviewRepo.save(interview);
        audit.record("ADD_NOTE", "Interview", interview.getId(), "note by " + authorRole);
        return interview;
    }

    public com.smarthire.repository.InterviewRepository repo() {
        return interviewRepo;
    }

    public List<Interview> assignmentsSorted(Long interviewerId) {
        return assignedTo(interviewerId).stream()
                .sorted(Comparator.comparing(Interview::getScheduledAt))
                .toList();
    }
}
