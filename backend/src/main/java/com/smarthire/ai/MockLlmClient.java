package com.smarthire.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Deterministic, offline mock implementation used when no LLM_API_KEY is configured.
 *
 * resumeMatch: parses the JD's must-have skills (from the jdText contract built by
 * ScreeningService) and scores = fraction of must-haves present in the résumé.
 *
 * answerScore: understands the banded rubric JSON (score_5 / score_3 / score_0
 * criteria arrays, per the Input_Data dataset). Criterion "hit" = ≥ half of its
 * significant tokens appear in the answer. Banding: ≥2 top-band hits → 5;
 * 1 top-band hit or any mid-band hit → 3; else 0. Falls back to flat keyword
 * matching if the rubric is not banded JSON.
 */
// Kept as a plain class for deterministic unit tests; production wiring uses SpringAiLlmClient.
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);
    private static final Set<String> STOPWORDS = Set.of(
            "mentions", "explains", "explaining", "defines", "mention", "correctly",
            "provides", "least", "with", "only", "that", "this", "have", "been",
            "into", "from", "such", "when", "than", "more", "very", "code-level");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ResumeMatchResult resumeMatch(String jdText, String resumeText) {
        List<String> skills = extractMustHaveSkills(jdText);
        if (skills.isEmpty()) {
            skills = Arrays.asList("java", "spring", "sql");  // defensive fallback
        }
        // naive keyword matching cannot read negation — drop sentences like
        // "No SQLAlchemy experience" / "has not used pytest" before matching
        String resume = positiveStatements(resumeText.toLowerCase(Locale.ROOT));
        List<String> matched = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        for (String skill : skills) {
            if (resume.contains(skill.toLowerCase(Locale.ROOT))) matched.add(skill);
            else gaps.add(skill);
        }
        double score = Math.round(100.0 * matched.size() / skills.size());
        double confidence = matched.isEmpty() ? 0.4 : 0.85;
        String summary = "Matched " + matched.size() + " of " + skills.size()
                + " must-have skills from the job description.";
        log.debug("Mock resumeMatch: score={} matched={} gaps={}", score, matched, gaps);
        return new ResumeMatchResult(score, matched, gaps, summary, confidence);
    }

    /** Removes sentences that negate skills so keyword matching ignores them. */
    private String positiveStatements(String text) {
        StringBuilder sb = new StringBuilder();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            String s = " " + sentence + " ";
            if (s.contains(" no ") || s.contains("not ") || s.contains("never ")
                    || s.contains("limited ") || s.contains("minimal ")
                    || s.contains("without ") || s.contains("hasn't") || s.contains("has not")) {
                continue;
            }
            sb.append(sentence).append(". ");
        }
        return sb.toString();
    }

    /** ScreeningService passes: "<title> | must-have: A, B, C | nice-to-have: ... | <summary>" */
    private List<String> extractMustHaveSkills(String jdText) {
        List<String> skills = new ArrayList<>();
        int start = jdText.indexOf("must-have:");
        int end = jdText.indexOf("| nice-to-have");
        if (start >= 0 && end > start) {
            for (String s : jdText.substring(start + "must-have:".length(), end).split(",")) {
                String skill = s.trim();
                if (!skill.isEmpty()) skills.add(skill);
            }
        }
        return skills;
    }

    @Override
    public AnswerScoreResult answerScore(String question, String referenceAnswer, String rubric, String answerText) {
        String answer = answerText == null ? "" : answerText.toLowerCase(Locale.ROOT);

        RubricResult banded = tryBandedRubric(rubric, answer);
        if (banded != null) {
            log.debug("Mock answerScore (banded): score={} hits={}", banded.score, banded.hits);
            return new AnswerScoreResult(banded.score, banded.justification, confidence(answerText), banded.hits);
        }

        // fallback: flat keyword rubric (semicolon/comma separated)
        List<String> hits = new ArrayList<>();
        List<String> keywords = new ArrayList<>();
        if (rubric != null && !rubric.isBlank()) {
            for (String k : rubric.split("[;,]")) {
                if (!k.isBlank()) keywords.add(k.trim().toLowerCase(Locale.ROOT));
            }
        }
        for (String k : keywords) {
            if (answer.contains(k)) hits.add(k);
        }
        double score = keywords.isEmpty() ? 3.0
                : Math.min(5.0, Math.round(5.0 * hits.size() / keywords.size() * 2) / 2.0);
        String justification = "Rubric hits: " + hits + " out of " + keywords + ".";
        log.debug("Mock answerScore (flat): score={} hits={}", score, hits);
        return new AnswerScoreResult(score, justification, confidence(answerText), hits);
    }

    private record RubricResult(double score, String justification, List<String> hits) {}

    private RubricResult tryBandedRubric(String rubricJson, String answer) {
        try {
            JsonNode root = mapper.readTree(rubricJson);
            if (!root.has("score_5")) return null;

            List<String> topHits = new ArrayList<>();
            for (JsonNode c : root.path("score_5")) {
                if (criterionHit(c.asText(), answer)) topHits.add(c.asText());
            }
            List<String> midHits = new ArrayList<>();
            for (JsonNode c : root.path("score_3")) {
                if (criterionHit(c.asText(), answer)) midHits.add(c.asText());
            }

            double score;
            if (topHits.size() >= 2) score = 5.0;
            else if (topHits.size() == 1 || !midHits.isEmpty()) score = 3.0;
            else score = 0.0;

            List<String> hits = new ArrayList<>(topHits);
            hits.addAll(midHits);
            String justification = "Top-band criteria hit: " + topHits.size()
                    + "; partial-band hints: " + midHits.size() + ".";
            return new RubricResult(score, justification, hits);
        } catch (Exception e) {
            return null;  // not banded JSON → caller falls back
        }
    }

    /** Criterion hit = at least half of its significant tokens appear in the answer. */
    private boolean criterionHit(String criterion, String answer) {
        List<String> tokens = new ArrayList<>();
        for (String raw : criterion.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (raw.length() >= 4 && !STOPWORDS.contains(raw)) tokens.add(raw);
        }
        if (tokens.isEmpty()) return false;
        long found = tokens.stream().filter(answer::contains).count();
        return found * 2 >= tokens.size();
    }

    private double confidence(String answerText) {
        return answerText == null || answerText.length() < 40 ? 0.5 : 0.82;
    }
}
