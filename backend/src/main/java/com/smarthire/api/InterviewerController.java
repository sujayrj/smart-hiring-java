package com.smarthire.api;

import com.smarthire.domain.Application;
import com.smarthire.domain.Candidate;
import com.smarthire.domain.JobDescription;
import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.CandidateRepository;
import com.smarthire.repository.JdRepository;
import com.smarthire.security.JwtService;
import com.smarthire.service.InterviewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InterviewerController {

    public record AssignmentView(Long interviewId, Long applicationId, Long candidateId,
                                 String candidateName, String jdTitle, String scheduledAt,
                                 String status, String decision) {}

    private final InterviewService interviewService;
    private final ApplicationRepository appRepo;
    private final CandidateRepository candidateRepo;
    private final JdRepository jdRepo;

    public InterviewerController(InterviewService interviewService, ApplicationRepository appRepo,
                                 CandidateRepository candidateRepo, JdRepository jdRepo) {
        this.interviewService = interviewService;
        this.appRepo = appRepo;
        this.candidateRepo = candidateRepo;
        this.jdRepo = jdRepo;
    }

    /** §7.4 step 2: interviewer sees assigned candidates ONLY — enforced by query, not UI. */
    @GetMapping("/interviewer/assignments")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public List<AssignmentView> myAssignments(Authentication auth) {
        JwtService.Principal p = principal(auth);
        return interviewService.assignmentsSorted(p.userId()).stream()
                .map(i -> {
                    Application app = appRepo.findById(i.getApplicationId()).orElse(null);
                    Candidate c = app == null ? null : candidateRepo.findById(app.getCandidateId()).orElse(null);
                    JobDescription jd = app == null ? null : jdRepo.findById(app.getJdId()).orElse(null);
                    return new AssignmentView(i.getId(), i.getApplicationId(),
                            c == null ? null : c.getId(),
                            c == null ? "unknown" : c.getName(),
                            jd == null ? null : jd.getTitle(),
                            i.getScheduledAt().toString(),
                            i.getStatus().name(),
                            i.getDecision() == null ? null : i.getDecision().name());
                })
                .toList();
    }

    static JwtService.Principal principal(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof JwtService.Principal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
