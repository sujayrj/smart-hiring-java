package com.smarthire.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "candidates")
public class Candidate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private String name;

    private String email;

    @Column(name = "applied_jd")
    private Long appliedJd;

    @Column(name = "experience_years")
    private int experienceYears;

    private String education;
    private String location;

    @Column(name = "profile_type")
    private String profileType;

    @Column(columnDefinition = "TEXT")
    private String resume;

    @Column(nullable = false)
    private String status = "APPLIED";

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Long getAppliedJd() { return appliedJd; }
    public void setAppliedJd(Long appliedJd) { this.appliedJd = appliedJd; }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }
    public String getEducation() { return education; }
    public void setEducation(String education) { this.education = education; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getProfileType() { return profileType; }
    public void setProfileType(String profileType) { this.profileType = profileType; }
    public String getResume() { return resume; }
    public void setResume(String resume) { this.resume = resume; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
