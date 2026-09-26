package com.smarthire.service;

import com.smarthire.domain.*;
import com.smarthire.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Candidate/Interviewer evidence visibility rules (§7.3 step 5, §8.3, §11):
 * candidates see aggregates only; interviewers see full evidence but only for
 * assigned candidates; admin sees everything.
 */
@Service
public class AccessService {

    private final CandidateRepository candidateRepo;
    private final InterviewRepository interviewRepo;
    private final ApplicationRepository appRepo;

    public AccessService(CandidateRepository candidateRepo, InterviewRepository interviewRepo,
                         ApplicationRepository appRepo) {
        this.candidateRepo = candidateRepo;
        this.interviewRepo = interviewRepo;
        this.appRepo = appRepo;
    }

    public record Principal(Long userId, String username, String role) {}

    public void assertCanViewApplication(Long applicationId, Principal p) {
        if (p.role().equals("ADMIN")) return;
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (p.role().equals("CANDIDATE")) {
            Candidate c = candidateRepo.findById(app.getCandidateId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (p.userId().equals(c.getUserId())) return;
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your application");
        }
        if (p.role().equals("INTERVIEWER")) {
            interviewRepo.findByApplicationId(applicationId)
                    .filter(i -> i.getInterviewerId().equals(p.userId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to you"));
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    /**
     * Full-evidence gate (résumé scores, AI justifications, rubric hits, flags).
     * UseCase lines 104/123 vs 117/172/231: Admin + assigned Interviewer only —
     * Candidates must NEVER reach this data, even for their own application.
     */
    public void assertCanViewFullEvidence(Long applicationId, Principal p) {
        if (p.role().equals("ADMIN")) return;
        if (p.role().equals("INTERVIEWER")) {
            interviewRepo.findByApplicationId(applicationId)
                    .filter(i -> i.getInterviewerId().equals(p.userId()))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Not assigned to you"));
            return;
        }
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN, "Candidates cannot view internal AI evidence");
    }

    /** Candidate-owner-only gate (my-result: aggregate + breakdown, no justifications). */
    public void assertCandidateOwner(Long applicationId, Principal p) {
        if (!p.role().equals("CANDIDATE")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Candidate endpoint");
        }
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Candidate c = candidateRepo.findById(app.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!p.userId().equals(c.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your application");
        }
    }
}
