package com.smarthire.search;

import com.smarthire.model.Job;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A small, from-scratch information-retrieval engine used for ranked job search.
 *
 * This is intentionally NOT a simple `String.contains()` filter. It builds a
 * real inverted index and scores documents with TF-IDF + cosine similarity -
 * the same core algorithm real search engines use, just at a scale
 * appropriate for a college project. Everything here is plain
 * java.util (HashMap, List, regex) - no external search library.
 *
 * How it works:
 *   1. Each job is tokenized into words (title + department + description + skills).
 *   2. An inverted index maps term -> {jobId -> term frequency in that job}.
 *   3. IDF (inverse document frequency) is computed per term: how rare it is
 *      across all jobs. Rare, distinctive terms score higher than common ones.
 *   4. A query is treated as a tiny "document" and converted to the same
 *      TF-IDF vector space.
 *   5. Jobs are ranked by cosine similarity between the query vector and each
 *      job's vector - i.e. how closely their word distributions point in the
 *      same "direction", regardless of document length.
 */
public class SearchEngine {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]+");
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "a", "an", "the", "and", "or", "of", "to", "in", "for", "with", "on", "is", "at"
    ));

    /** term -> (jobId -> raw term frequency in that job's document) */
    private final Map<String, Map<Integer, Integer>> invertedIndex = new HashMap<>();
    /** jobId -> total token count in that job's document (for TF normalization) */
    private final Map<Integer, Integer> docLengths = new HashMap<>();
    private final Map<Integer, Job> jobsById = new HashMap<>();
    private int documentCount = 0;

    public static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher m = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String tok = m.group();
            if (!STOPWORDS.contains(tok)) tokens.add(tok);
        }
        return tokens;
    }

    /** Rebuilds the index from scratch. Called before every search - fine at this data scale. */
    public void build(List<Job> jobs) {
        invertedIndex.clear();
        docLengths.clear();
        jobsById.clear();
        documentCount = jobs.size();

        for (Job job : jobs) {
            jobsById.put(job.getId(), job);
            List<String> tokens = tokenize(documentText(job));
            docLengths.put(job.getId(), tokens.size());
            Map<String, Integer> termCounts = new HashMap<>();
            for (String t : tokens) termCounts.merge(t, 1, Integer::sum);
            for (Map.Entry<String, Integer> e : termCounts.entrySet()) {
                invertedIndex.computeIfAbsent(e.getKey(), k -> new HashMap<>()).put(job.getId(), e.getValue());
            }
        }
    }

    private String documentText(Job job) {
        return job.getTitle() + " " + job.getDepartment() + " " + job.getDescription()
                + " " + String.join(" ", job.getRequiredSkills());
    }

    /** Inverse document frequency: log(N / (1 + docsContainingTerm)), so rarer terms score higher. */
    private double idf(String term) {
        int df = invertedIndex.getOrDefault(term, Collections.emptyMap()).size();
        return Math.log((double) (documentCount + 1) / (df + 1)) + 1.0;
    }

    /**
     * Ranks all indexed jobs against a free-text query using cosine similarity
     * over TF-IDF vectors. Returns only jobs with a positive score, best first.
     */
    public List<SearchResult> search(String query) {
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return Collections.emptyList();

        Map<String, Integer> queryTermCounts = new HashMap<>();
        for (String t : queryTokens) queryTermCounts.merge(t, 1, Integer::sum);

        Map<String, Double> queryVector = new HashMap<>();
        for (Map.Entry<String, Integer> e : queryTermCounts.entrySet()) {
            queryVector.put(e.getKey(), e.getValue() * idf(e.getKey()));
        }
        double queryNorm = vectorNorm(queryVector.values());

        List<SearchResult> results = new ArrayList<>();
        for (Integer jobId : jobsById.keySet()) {
            double dot = 0.0;
            double docNormSquared = 0.0;
            int docLen = Math.max(1, docLengths.getOrDefault(jobId, 1));

            // Accumulate dot product only over query terms (sparse - the rest contribute 0 to dot product).
            for (Map.Entry<String, Double> qe : queryVector.entrySet()) {
                Map<Integer, Integer> postings = invertedIndex.get(qe.getKey());
                if (postings == null) continue;
                Integer tf = postings.get(jobId);
                if (tf == null) continue;
                double docWeight = ((double) tf / docLen) * idf(qe.getKey());
                dot += qe.getValue() * docWeight;
            }

            // Full document norm (for correct cosine denominator), computed over the doc's own terms.
            for (Map.Entry<String, Map<Integer, Integer>> entry : invertedIndex.entrySet()) {
                Integer tf = entry.getValue().get(jobId);
                if (tf == null) continue;
                double w = ((double) tf / docLen) * idf(entry.getKey());
                docNormSquared += w * w;
            }
            double docNorm = Math.sqrt(docNormSquared);

            if (dot > 0 && queryNorm > 0 && docNorm > 0) {
                double cosineSimilarity = dot / (queryNorm * docNorm);
                results.add(new SearchResult(jobsById.get(jobId), cosineSimilarity));
            }
        }

        return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .collect(Collectors.toList());
    }

    private double vectorNorm(Collection<Double> weights) {
        double sumSquares = 0.0;
        for (double w : weights) sumSquares += w * w;
        return Math.sqrt(sumSquares);
    }

    /** A job paired with its cosine-similarity relevance score (0.0-1.0-ish) for a given query. */
    public static class SearchResult {
        private final Job job;
        private final double score;

        public SearchResult(Job job, double score) {
            this.job = job;
            this.score = score;
        }

        public Job getJob() { return job; }
        public double getScore() { return score; }
    }
}
