package com.smarthire.service;

import com.smarthire.ai.LlmClient;
import com.smarthire.domain.*;
import com.smarthire.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Candidate Q&A (§7.2) + scoring fusion (§7.3).
 * One question at a time; timeout auto-submit handled client-side but flagged here;
 * each answer scored via answer_score and persisted per question.
 * When all questions are answered: normalize Q&A to 0-100, apply JD weights,
 * compute combined score and PASS/HOLD/REJECT band.
 */
@Service
public class QaService {

    private final ApplicationRepository appRepo;
    private final QuestionRepository questionRepo;
    private final AnswerRepository answerRepo;
    private final JdRepository jdRepo;
    private final CandidateRepository candidateRepo;
    private final LlmClient llm;
    private final AuditService audit;

    public QaService(ApplicationRepository appRepo, QuestionRepository questionRepo,
                     AnswerRepository answerRepo, JdRepository jdRepo,
                     CandidateRepository candidateRepo, LlmClient llm, AuditService audit) {
        this.appRepo = appRepo;
        this.questionRepo = questionRepo;
        this.answerRepo = answerRepo;
        this.jdRepo = jdRepo;
        this.candidateRepo = candidateRepo;
        this.llm = llm;
        this.audit = audit;
    }

    public record QuestionView(Long id, String text, int timeLimitSeconds, int index, int total) {}

    public record AnswerAck(Long answerId, Double score, boolean qaComplete) {}

    public List<QuestionView> getQuestions(Long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        if (app.getStatus() != ApplicationStatus.SCREENING && app.getStatus() != ApplicationStatus.QA_IN_PROGRESS) {
            throw new IllegalStateException("Application is not eligible for Q&A (status=" + app.getStatus() + ")");
        }
        List<Question> questions = questionRepo.findByJdIdOrderByOrderIndex(app.getJdId());
        List<Answer> answered = answerRepo.findByApplicationId(applicationId);
        return questions.stream()
                .skip(answered.size())   // one-at-a-time: only the next unanswered question onward
                .map(q -> new QuestionView(q.getId(), q.getText(), q.getTimeLimitSeconds(),
                        questions.indexOf(q) + 1, questions.size()))
                .toList();
    }

    @Transactional
    public AnswerAck submitAnswer(Long applicationId, Long questionId, String answerText, boolean timeout, Integer timeTakenSeconds) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        Question q = questionRepo.findById(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found"));
        if (!q.getJdId().equals(app.getJdId())) {
            throw new IllegalArgumentException("Question does not belong to this application's JD");
        }
        if (answerRepo.findByApplicationId(applicationId).stream().anyMatch(a -> a.getQuestionId().equals(questionId))) {
            throw new IllegalStateException("Question already answered");
        }

        LlmClient.AnswerScoreResult r = llm.answerScore(q.getText(), q.getReferenceAnswer(), q.getRubric(), answerText);

        // persist validated scoring (§13) before status updates
        Answer answer = new Answer();
        answer.setApplicationId(applicationId);
        answer.setQuestionId(questionId);
        answer.setAnswerText(answerText);
        answer.setScore(r.score());
        answer.setConfidence(r.confidence());
        answer.setRubricHits(r.rubricHits());
        answer.setTimeoutSubmit(timeout);
        answer.setTimeTakenSeconds(timeTakenSeconds);
        answerRepo.save(answer);

        app.setStatus(ApplicationStatus.QA_IN_PROGRESS);
        appRepo.save(app);
        audit.record("SUBMIT_ANSWER", "Application", applicationId,
                "question=" + questionId + " timeout=" + timeout);

        long answered = answerRepo.findByApplicationId(applicationId).size();
        long total = questionRepo.findByJdIdOrderByOrderIndex(app.getJdId()).size();
        boolean complete = answered >= total;
        if (complete) {
            fuse(applicationId);
        }
        return new AnswerAck(answer.getId(), r.score(), complete);
    }

    /** §7.3: normalize Q&A to 0-100, apply JD weights, compute band. */
    @Transactional
    public void fuse(Long applicationId) {
        Application app = appRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found"));
        JobDescription jd = jdRepo.findById(app.getJdId())
                .orElseThrow(() -> new IllegalArgumentException("JD not found"));

        List<Answer> answers = answerRepo.findByApplicationId(applicationId);
        if (answers.isEmpty()) return;

        double avg = answers.stream().mapToDouble(a -> a.getScore() == null ? 0 : a.getScore()).average().orElse(0);
        double qaNorm = (avg / 5.0) * 100.0;
        double resume = app.getResumeScore() == null ? 0 : app.getResumeScore();

        double combined = jd.getResumeWeight() * resume0To100(app.getResumeScore())
                + jd.getQaWeight() * qaNorm;

        app.setQaScore(qaNorm);
        app.setCombinedScore(combined);
        Band band = determineBand(combined, jd.getPassThreshold());
        app.setBand(band);
        app.setStatus(ApplicationStatus.FUSED);
        app.setNextSteps(switch (band) {
            case PASS -> "You are moving forward. The hiring team will contact you.";
            case HOLD -> "Your application is under further human review.";
            case REJECT -> "Thank you for your interest.";
        });
        appRepo.save(app);
        audit.record("SCORING_COMPLETE", "Application", applicationId,
                "combined=" + Math.round(combined) + " band=" + band);
    }

    private double resume0To100(Double resumeScore) {
        return resumeScore == null ? 0 : resumeScore;
    }

    private Band determineBand(double combined, double passThreshold) {
        if (combined >= passThreshold) return Band.PASS;
        if (combined >= passThreshold * 0.8) return Band.HOLD;
        return Band.REJECT;
    }
}
