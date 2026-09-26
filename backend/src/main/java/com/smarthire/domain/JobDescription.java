package com.smarthire.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "job_descriptions")
public class JobDescription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank private String title;
    private String location;

    @Column(name = "experience_years")
    private int experienceYears;

    private String education;

    @Column(name = "must_have", columnDefinition = "TEXT")
    private String mustHave;

    @Column(name = "nice_to_have", columnDefinition = "TEXT")
    private String niceToHave;

    @Column(name = "resume_weight", nullable = false)
    private double resumeWeight = 0.6;

    @Column(name = "qa_weight", nullable = false)
    private double qaWeight = 0.4;

    @Column(name = "pass_threshold", nullable = false)
    private double passThreshold = 65.0;

    @Column(name = "confidence_cutoff", nullable = false)
    private double confidenceCutoff = 0.6;

    @Column(columnDefinition = "TEXT")
    private String summary;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getMustHave() { return mustHave; }
    public void setMustHave(String mustHave) { this.mustHave = mustHave; }
    public String getNiceToHave() { return niceToHave; }
    public void setNiceToHave(String niceToHave) { this.niceToHave = niceToHave; }
    public double getResumeWeight() { return resumeWeight; }
    public void setResumeWeight(double resumeWeight) { this.resumeWeight = resumeWeight; }
    public double getQaWeight() { return qaWeight; }
    public void setQaWeight(double qaWeight) { this.qaWeight = qaWeight; }
    public double getPassThreshold() { return passThreshold; }
    public void setPassThreshold(double passThreshold) { this.passThreshold = passThreshold; }
    public double getConfidenceCutoff() { return confidenceCutoff; }
    public void setConfidenceCutoff(double confidenceCutoff) { this.confidenceCutoff = confidenceCutoff; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
