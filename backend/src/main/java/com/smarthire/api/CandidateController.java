package com.smarthire.api;

import com.smarthire.domain.Application;
import com.smarthire.domain.Band;
import com.smarthire.domain.Candidate;
import com.smarthire.domain.Interview;
import com.smarthire.domain.JobDescription;
import com.smarthire.domain.User;
import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.CandidateRepository;
import com.smarthire.repository.InterviewRepository;
import com.smarthire.repository.JdRepository;
import com.smarthire.repository.UserRepository;
import com.smarthire.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

/**
 * "Candidate sees THEIR OWN live application status" (UseCase lines 112–117, 211).
 *
 * Identity is derived from the JWT — the browser never supplies a candidate id or
 * application id. Response is candidate-safe: status, band, next steps, interview
 * name/date only. No résumé scores, AI evidence, rubric hits or flags (§8.3, §11).
 */
@RestController
@RequestMapping("/api/me")
public class CandidateController {

    /** Candidate-safe application view — no AI evidence fields. */
    public record MeApplicationView(Long id, Long jdId, String jdTitle, String status, Band band,
                                    String nextSteps, String interviewerName, String interviewAt) {}

    public record CandidateHomeDto(String candidateName, String candidateStatus,
                                   List<MeApplicationView> applications) {}

    private final CandidateRepository candidateRepo;
    private final ApplicationRepository appRepo;
    private final JdRepository jdRepo;
    private final UserRepository userRepo;
    private final InterviewRepository interviewRepo;

    public CandidateController(CandidateRepository candidateRepo, ApplicationRepository appRepo,
                               JdRepository jdRepo, UserRepository userRepo,
                               InterviewRepository interviewRepo) {
        this.candidateRepo = candidateRepo;
        this.appRepo = appRepo;
        this.jdRepo = jdRepo;
        this.userRepo = userRepo;
        this.interviewRepo = interviewRepo;
    }

    @GetMapping("/applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateHomeDto myApplications(Authentication auth) {
        JwtService.Principal p = principal(auth);

        Candidate candidate = candidateRepo.findByUserId(p.userId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Candidate profile not found"));

        List<MeApplicationView> views = new ArrayList<>();
        for (Application app : appRepo.findByCandidateId(candidate.getId())) {
            JobDescription jd = jdRepo.findById(app.getJdId()).orElse(null);

            String interviewerName = null;
            String interviewAt = null;
            var optInt = interviewRepo.findByApplicationId(app.getId());
            if (optInt.isPresent()) {
                Interview i = optInt.get();
                interviewerName = userRepo.findById(i.getInterviewerId())
                        .map(User::getUsername).orElse(null);
                interviewAt = i.getScheduledAt().toString();
            }

            views.add(new MeApplicationView(
                    app.getId(), app.getJdId(),
                    jd == null ? null : jd.getTitle(),
                    app.getStatus().name(), app.getBand(),
                    app.getNextSteps(), interviewerName, interviewAt));
        }

        return new CandidateHomeDto(candidate.getName(), candidate.getStatus(), views);
    }

    static JwtService.Principal principal(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof JwtService.Principal p) {
            return p;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
