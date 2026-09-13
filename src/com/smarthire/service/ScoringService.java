package com.smarthire.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Simulates the "AI resume scoring" feature of the original SmartHire app.
 * The original used an ML-ish weighting service; here we implement a
 * transparent, explainable keyword-overlap score using core Java only
 * (java.util.Set intersection), which is well suited to a college viva
 * since the logic can be explained line by line.
 *
 * Score = (matched required skills / total required skills) * 100
 */
public class ScoringService {

    public double computeScore(List<String> resumeSkills, List<String> requiredSkills) {
        if (requiredSkills == null || requiredSkills.isEmpty()) return 0.0;

        Set<String> resumeSet = normalize(resumeSkills);
        Set<String> requiredSet = normalize(requiredSkills);

        long matched = requiredSet.stream().filter(resumeSet::contains).count();
        return (matched * 100.0) / requiredSet.size();
    }

    /**
     * Returns which required skills the resume is missing - useful for a
     * candidate deciding whether/how to tailor an application before applying.
     */
    public List<String> missingSkills(List<String> resumeSkills, List<String> requiredSkills) {
        Set<String> resumeSet = normalize(resumeSkills);
        return requiredSkills.stream()
                .filter(s -> !resumeSet.contains(s.trim().toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
    }

    private Set<String> normalize(List<String> skills) {
        return skills.stream()
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
