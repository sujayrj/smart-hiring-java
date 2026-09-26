package com.smarthire.ai;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Port for LLM providers (spec §8: provider abstraction, prompts, schema validation,
 * one-retry guardrail). Implemented by the Spring AI adapter.
 */
public interface LlmClient {

    record ResumeMatchResult(double score, java.util.List<String> matchedSkills,
                             java.util.List<String> gaps, String summary, double confidence) {}

    record AnswerScoreResult(double score, String justification, double confidence,
                             java.util.List<String> rubricHits) {}

    ResumeMatchResult resumeMatch(String jdText, String resumeText);

    AnswerScoreResult answerScore(String question, String referenceAnswer, String rubric, String answerText);

}
