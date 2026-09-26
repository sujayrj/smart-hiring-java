package com.smarthire.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "answers")
public class Answer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    private Double score;
    private Double confidence;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "answer_rubric_hits", joinColumns = @JoinColumn(name = "answer_id"))
    @Column(name = "rubric_hit")
    private List<String> rubricHits = new ArrayList<>();

    @Column(name = "timeout_submit", nullable = false)
    private boolean timeoutSubmit = false;

    @Column(name = "time_taken_s")
    private Integer timeTakenSeconds;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    public Long getId() { return id; }
    public Long getApplicationId() { return applicationId; }
    public void setApplicationId(Long applicationId) { this.applicationId = applicationId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public List<String> getRubricHits() { return rubricHits; }
    public void setRubricHits(List<String> rubricHits) { this.rubricHits = rubricHits; }
    public boolean isTimeoutSubmit() { return timeoutSubmit; }
    public void setTimeoutSubmit(boolean timeoutSubmit) { this.timeoutSubmit = timeoutSubmit; }
    public Integer getTimeTakenSeconds() { return timeTakenSeconds; }
    public void setTimeTakenSeconds(Integer timeTakenSeconds) { this.timeTakenSeconds = timeTakenSeconds; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
}
