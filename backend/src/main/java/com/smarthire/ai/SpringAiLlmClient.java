package com.smarthire.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production LLM adapter. Spring AI owns the provider-specific HTTP client;
 * this class keeps SmartHire's scoring contract and validation in one place.
 */
@Component
public class SpringAiLlmClient implements LlmClient {

    private static final String JSON_ONLY = "Return only one valid JSON object. Do not use markdown fences.";
    private static final String RETRY = "The previous response did not match the required JSON contract. "
            + JSON_ONLY;

    private final ChatClient chatClient;
    private final ObjectMapper mapper;

    public SpringAiLlmClient(ChatClient.Builder chatClientBuilder, ObjectMapper mapper) {
        this.chatClient = chatClientBuilder.build();
        this.mapper = mapper;
    }

    @Override
    public ResumeMatchResult resumeMatch(String jdText, String resumeText) {
        String prompt = """
                Evaluate this candidate resume against the job description.
                Score only evidence present in the resume. Do not infer missing skills.

                Required JSON shape:
                {"score": number 0-100, "matchedSkills": [string], "gaps": [string],
                 "summary": string, "confidence": number 0-1}

                JOB DESCRIPTION:
                %s

                RESUME:
                %s

                %s
                """.formatted(jdText, resumeText, JSON_ONLY);

        return callWithOneRetry(prompt, ResumeMatchResult.class, "resume_match");
    }

    @Override
    public AnswerScoreResult answerScore(String question, String referenceAnswer,
                                         String rubric, String answerText) {
        String prompt = """
                Grade the candidate answer using the supplied rubric. Return a fair,
                concise justification grounded in the answer and identify the rubric
                criteria that were met.

                Required JSON shape:
                {"score": number 0-5, "justification": string,
                 "confidence": number 0-1, "rubricHits": [string]}

                QUESTION:
                %s

                REFERENCE ANSWER:
                %s

                RUBRIC:
                %s

                CANDIDATE ANSWER:
                %s

                %s
                """.formatted(question, referenceAnswer, rubric, answerText, JSON_ONLY);

        return callWithOneRetry(prompt, AnswerScoreResult.class, "answer_score");
    }

    private <T> T callWithOneRetry(String prompt, Class<T> type, String operation) {
        String first = generate(prompt);
        T parsed = parseAndValidate(first, type);
        if (parsed != null) return parsed;

        String second = generate(prompt + "\n\n" + RETRY);
        parsed = parseAndValidate(second, type);
        if (parsed != null) return parsed;

        throw new ControlledLlmException("Spring AI returned invalid " + operation + " JSON twice");
    }

    private String generate(String prompt) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            if (content == null || content.isBlank()) {
                throw new ControlledLlmException("Spring AI returned an empty response");
            }
            return content;
        } catch (ControlledLlmException e) {
            throw e;
        } catch (Exception e) {
            throw new ControlledLlmException("Spring AI request failed: " + e.getMessage());
        }
    }

    private <T> T parseAndValidate(String raw, Class<T> type) {
        try {
            T value = mapper.readValue(stripMarkdownFence(raw), type);
            validate(value);
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private void validate(Object value) {
        if (value instanceof ResumeMatchResult result
                && (result.score() < 0 || result.score() > 100
                || result.confidence() < 0 || result.confidence() > 1
                || result.matchedSkills() == null || result.gaps() == null
                || result.summary() == null)) {
            throw new IllegalArgumentException("Invalid resume match values");
        }
        if (value instanceof AnswerScoreResult result
                && (result.score() < 0 || result.score() > 5
                || result.confidence() < 0 || result.confidence() > 1
                || result.rubricHits() == null || result.justification() == null)) {
            throw new IllegalArgumentException("Invalid answer score values");
        }
    }

    private String stripMarkdownFence(String raw) {
        String value = raw.trim();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return value.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return value;
    }
}
