package com.smarthire.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "jd_id"}))
public class Application {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Column(name = "jd_id", nullable = false)
    private Long jdId;

    @Column(name = "resume_score")
    private Double resumeScore;

    @Column(name = "resume_confidence")
    private Double resumeConfidence;

    @Column(name = "qa_score")
    private Double qaScore;

    @Column(name = "combined_score")
    private Double combinedScore;

    @Enumerated(EnumType.STRING)
    private Band band;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "next_steps")
    private String nextSteps;

    @Column(name = "resume_matched")
    private String resumeMatched;

    @Column(name = "resume_gaps")
    private String resumeGaps;

    @Column(name = "resume_summary")
    private String resumeSummary;

    public Long getId() { return id; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public Long getJdId() { return jdId; }
    public void setJdId(Long jdId) { this.jdId = jdId; }
    public Double getResumeScore() { return resumeScore; }
    public void setResumeScore(Double resumeScore) { this.resumeScore = resumeScore; }
    public Double getResumeConfidence() { return resumeConfidence; }
    public void setResumeConfidence(Double resumeConfidence) { this.resumeConfidence = resumeConfidence; }
    public Double getQaScore() { return qaScore; }
    public void setQaScore(Double qaScore) { this.qaScore = qaScore; }
    public Double getCombinedScore() { return combinedScore; }
    public void setCombinedScore(Double combinedScore) { this.combinedScore = combinedScore; }
    public Band getBand() { return band; }
    public void setBand(Band band) { this.band = band; }
    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }
    public String getNextSteps() { return nextSteps; }
    public void setNextSteps(String nextSteps) { this.nextSteps = nextSteps; }
    public String getResumeMatched() { return resumeMatched; }
    public void setResumeMatched(String resumeMatched) { this.resumeMatched = resumeMatched; }
    public String getResumeGaps() { return resumeGaps; }
    public void setResumeGaps(String resumeGaps) { this.resumeGaps = resumeGaps; }
    public String getResumeSummary() { return resumeSummary; }
    public void setResumeSummary(String resumeSummary) { this.resumeSummary = resumeSummary; }
}
