// ── SmartHire · src/main/java/com/smarthire/service/AiScoringService.java ──
package com.smarthire.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.domain.entities.Application;
import com.smarthire.domain.repositories.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiScoringService {

    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    @Value("${smarthire.claude.api-key:}")
    private String claudeApiKey;

    @Value("${smarthire.claude.api-url}")
    private String claudeApiUrl;

    @Value("${smarthire.claude.model}")
    private String claudeModel;

    @Value("${smarthire.claude.enabled:true}")
    private boolean claudeEnabled;

    /**
     * Pre-computed IDF weights from a 50,000-document corpus (idf-corpus.json).
     * Loaded at startup — replaces the original toy 2-doc IDF computation.
     * Terms not in the corpus fall back to idf=1.0 (neutral weight).
     */
    private Map<String, Double> corpusIdf = new java.util.HashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.io.ResourceLoader resourceLoader;

    @jakarta.annotation.PostConstruct
    void loadIdfCorpus() {
        try {
            var resource = resourceLoader.getResource("classpath:idf-corpus.json");
            var tree     = objectMapper.readTree(resource.getInputStream());
            var termsNode = tree.get("terms");
            if (termsNode != null) {
                termsNode.fields().forEachRemaining(e ->
                    corpusIdf.put(e.getKey(), e.getValue().asDouble(1.0)));
            }
            log.info("IDF corpus loaded: {} terms (corpus_size={})",
                corpusIdf.size(), tree.has("corpus_size") ? tree.get("corpus_size").asInt() : "?");
        } catch (Exception e) {
            log.warn("Could not load idf-corpus.json — falling back to smoothed IDF: {}", e.getMessage());
        }
    }

    // ── English stopwords ──────────────────────────────────────────────────────
    private static final Set<String> STOPWORDS = Set.of(
        "a","an","the","and","or","but","in","on","at","to","for","of","with",
        "by","from","as","is","was","are","were","be","been","being","have",
        "has","had","do","does","did","will","would","could","should","may",
        "might","shall","can","not","no","nor","so","yet","both","either",
        "neither","each","few","more","most","other","some","such","than",
        "then","that","this","these","those","it","its","we","they","their",
        "our","your","my","i","he","she","you","who","which","what","when",
        "where","how","why","all","any","both","much","many","own","same",
        "just","because","if","while","although","though","since","after",
        "before","during","about","above","below","between","into","through",
        "during","including","until","against","among","throughout","despite"
    );

    // ── Stage 1: TF-IDF Cosine Similarity ────────────────────────────────────

    public double computeCosineSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.isBlank() || text2.isBlank()) {
            return 0.0;
        }
        List<String> tokens1 = tokenise(text1);
        List<String> tokens2 = tokenise(text2);

        if (tokens1.isEmpty() || tokens2.isEmpty()) return 0.0;

        Set<String> vocabulary = new HashSet<>(tokens1);
        vocabulary.addAll(tokens2);

        // Term frequency per document
        Map<String, Long> tf1 = tokens1.stream()
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        Map<String, Long> tf2 = tokens2.stream()
            .collect(Collectors.groupingBy(t -> t, Collectors.counting()));

        // IDF: use pre-computed corpus weights (50k documents) for known terms.
        // For unknown terms, fall back to smoothed document-frequency over the 2-doc mini-corpus.
        Map<String, Double> idf = new HashMap<>();
        for (String term : vocabulary) {
            if (!corpusIdf.isEmpty() && corpusIdf.containsKey(term)) {
                // Corpus-based IDF — much more meaningful than 2-doc approximation
                idf.put(term, corpusIdf.get(term));
            } else {
                // Unknown term — fall back to smoothed IDF (rare = high weight)
                int df = (tf1.containsKey(term) ? 1 : 0) + (tf2.containsKey(term) ? 1 : 0);
                idf.put(term, Math.log((2.0 + 1) / (df + 1)) + 1.0);
            }
        }

        // TF-IDF vectors
        double[] vec1 = buildVector(vocabulary, tf1, idf, tokens1.size());
        double[] vec2 = buildVector(vocabulary, tf2, idf, tokens2.size());

        // Cosine similarity = dot(v1, v2) / (||v1|| * ||v2||)
        double dot = 0.0, norm1 = 0.0, norm2 = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dot   += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }
        if (norm1 == 0 || norm2 == 0) return 0.0;
        return dot / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private double[] buildVector(Set<String> vocabulary, Map<String, Long> tf,
                                  Map<String, Double> idf, int docSize) {
        String[] vocab = vocabulary.toArray(new String[0]);
        double[] vec = new double[vocab.length];
        for (int i = 0; i < vocab.length; i++) {
            String term = vocab[i];
            double termFreq = tf.getOrDefault(term, 0L) / (double) docSize;
            vec[i] = termFreq * idf.getOrDefault(term, 1.0);
        }
        return vec;
    }

    private List<String> tokenise(String text) {
        return Arrays.stream(text.toLowerCase()
            .replaceAll("[^a-z0-9\\s]", " ")
            .split("\\s+"))
            .filter(t -> t.length() > 2)
            .filter(t -> !STOPWORDS.contains(t))
            .map(this::stem)
            .filter(t -> !t.isEmpty())
            .collect(Collectors.toList());
    }

    /** Simple suffix-stripping stemmer (Porter-lite) */
    private String stem(String word) {
        if (word.endsWith("ing") && word.length() > 6)  return word.substring(0, word.length() - 3);
        if (word.endsWith("tion") && word.length() > 6) return word.substring(0, word.length() - 4);
        if (word.endsWith("ness") && word.length() > 6) return word.substring(0, word.length() - 4);
        if (word.endsWith("ment") && word.length() > 6) return word.substring(0, word.length() - 4);
        if (word.endsWith("able") && word.length() > 6) return word.substring(0, word.length() - 4);
        if (word.endsWith("ible") && word.length() > 6) return word.substring(0, word.length() - 4);
        if (word.endsWith("ies") && word.length() > 4)  return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("ed")  && word.length() > 4)  return word.substring(0, word.length() - 2);
        if (word.endsWith("er")  && word.length() > 4)  return word.substring(0, word.length() - 2);
        if (word.endsWith("ly")  && word.length() > 4)  return word.substring(0, word.length() - 2);
        if (word.endsWith("s")   && word.length() > 3 && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }

    // ── Stage 2: Claude Narrative Analysis ───────────────────────────────────

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> callClaude(String jobTitle, String requirements, String resumeText) {
        if (!claudeEnabled || claudeApiKey == null || claudeApiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String userPrompt = """
                JOB TITLE: %s
                JOB REQUIREMENTS: %s
                
                CANDIDATE RESUME TEXT: %s
                
                Respond ONLY with a JSON object (no prose, no markdown):
                {
                  "overall_score": <0-100>,
                  "skill_match_pct": <0-100>,
                  "strengths": ["...", "...", "..."],
                  "gaps": ["...", "..."],
                  "recommendation": "STRONG_YES | YES | MAYBE | NO",
                  "summary": "<2 sentence analyst note>"
                }
                """.formatted(jobTitle, requirements, resumeText);

            Map<String, Object> body = Map.of(
                "model", claudeModel,
                "max_tokens", 600,
                "system", "You are a senior HR analyst. Score the candidate resume against the job description. " +
                          "Respond ONLY with the requested JSON object. No prose, no markdown.",
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", claudeApiKey);
            headers.set("anthropic-version", "2023-06-01");

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(claudeApiUrl, HttpMethod.POST, entity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().get("content");
                if (content != null && !content.isEmpty()) {
                    String jsonText = (String) content.get(0).get("text");
                    return Optional.of(objectMapper.readValue(jsonText, Map.class));
                }
            }
        } catch (Exception e) {
            log.warn("Claude API call failed: {} — falling back to TF-IDF", e.getMessage());
        }
        return Optional.empty();
    }

    // ── Full async scoring pipeline ───────────────────────────────────────────

    @Async("scoringExecutor")
    @Transactional
    public void scoreApplicationAsync(UUID applicationId) {
        applicationRepository.findById(applicationId).ifPresent(app -> {
            try {
                score(app);
            } catch (Exception e) {
                log.error("Scoring failed for application {}: {}", applicationId, e.getMessage(), e);
                // Mark as complete with tfidf fallback to not block the candidate
                app.setKeywordMatches(Map.of("scoring_method", "failed", "error", e.getMessage()));
                app.setScoringComplete(true);
                applicationRepository.save(app);
            }
        });
    }

    private void score(Application app) {
        String resumeText = fetchResumeText(app.getResumeUrl());
        String jdText     = app.getJob().getRequirements() + " " + app.getJob().getDescription();
        String jobTitle   = app.getJob().getTitle();

        // Stage 1: TF-IDF
        double tfidfScore = computeCosineSimilarity(resumeText, jdText) * 100;

        // Stage 2: Claude
        var claudeResult = callClaude(jobTitle, app.getJob().getRequirements(), resumeText);

        Map<String, Object> keywordMatches = new HashMap<>();
        double finalScore;
        double skillMatchPct;
        String aiSummary;

        if (claudeResult.isPresent()) {
            var claude = claudeResult.get();
            finalScore    = toDouble(claude.get("overall_score"));
            skillMatchPct = toDouble(claude.get("skill_match_pct"));
            aiSummary     = (String) claude.get("summary");
            keywordMatches.put("scoring_method", "claude+tfidf");
            // Push real-time score to recruiters watching this application
            notificationService.pushScoringComplete(app.getId(), finalScore,
                skillMatchPct, recommendation, summary, "claude+tfidf");
            keywordMatches.put("tfidf_score",    round1(tfidfScore));
            keywordMatches.put("claude_score",   round1(finalScore));
            keywordMatches.put("strengths",      claude.get("strengths"));
            keywordMatches.put("gaps",           claude.get("gaps"));
            keywordMatches.put("recommendation", claude.get("recommendation"));
        } else {
            finalScore    = tfidfScore;
            skillMatchPct = tfidfScore * 0.9;
            aiSummary     = "Score computed via TF-IDF cosine similarity (%.1f%% match).".formatted(tfidfScore);
            keywordMatches.put("scoring_method", "tfidf_only");
            keywordMatches.put("tfidf_score",    round1(tfidfScore));
        }

        app.setAiScore(BigDecimal.valueOf(round1(finalScore)));
        app.setSkillMatchPct(BigDecimal.valueOf(round1(skillMatchPct)));
        app.setAiSummary(aiSummary);
        app.setKeywordMatches(keywordMatches);
        app.setScoringComplete(true);
        applicationRepository.save(app);

        log.info("Scored application {} — score={}", app.getId(), round1(finalScore));
    }

    private String fetchResumeText(String resumeUrl) {
        if (resumeUrl == null || resumeUrl.isBlank()) return "";
        try {
            return fileUploadService.extractResumeText(resumeUrl);
        } catch (Exception e) {
            log.warn("Resume text extraction failed (key={}): {} — scoring with empty text", resumeUrl, e.getMessage());
            return "";
        }
    }

    private double toDouble(Object val) {
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
