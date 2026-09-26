package com.smarthire.api;

import com.smarthire.domain.JobDescription;
import com.smarthire.repository.JdRepository;
import com.smarthire.service.AuditService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/jds")
public class JdController {

    public record JdRequest(@NotBlank String title, String location, int experienceYears,
                            String education, String mustHave, String niceToHave,
                            Double resumeWeight, Double qaWeight,
                            Double passThreshold, Double confidenceCutoff, String summary) {}

    private final JdRepository repo;
    private final AuditService audit;

    public JdController(JdRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<JobDescription> list() {
        return repo.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public JobDescription create(@Valid @RequestBody JdRequest req) {
        JobDescription jd = new JobDescription();
        apply(jd, req);
        jd = repo.save(jd);
        audit.record("CREATE_JD", "JD", jd.getId(), jd.getTitle());
        return jd;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public JobDescription get(@PathVariable Long id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JD not found"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public JobDescription update(@PathVariable Long id, @Valid @RequestBody JdRequest req) {
        JobDescription jd = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "JD not found"));
        apply(jd, req);
        jd = repo.save(jd);
        audit.record("UPDATE_JD", "JD", id, jd.getTitle());
        return jd;
    }

    private void apply(JobDescription jd, JdRequest req) {
        jd.setTitle(req.title());
        jd.setLocation(req.location());
        jd.setExperienceYears(req.experienceYears());
        jd.setEducation(req.education());
        jd.setMustHave(req.mustHave());
        jd.setNiceToHave(req.niceToHave());
        if (req.resumeWeight() != null) jd.setResumeWeight(req.resumeWeight());
        if (req.qaWeight() != null) jd.setQaWeight(req.qaWeight());
        if (req.passThreshold() != null) jd.setPassThreshold(req.passThreshold());
        if (req.confidenceCutoff() != null) jd.setConfidenceCutoff(req.confidenceCutoff());
        jd.setSummary(req.summary());
    }
}
