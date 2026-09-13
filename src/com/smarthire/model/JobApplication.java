package com.smarthire.model;

import com.smarthire.util.CsvUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a candidate's application to a specific job.
 * The "score" field simulates the AI-scoring feature of the original
 * SmartHire system using simple keyword-overlap matching (see ScoringService).
 */
public class JobApplication {

    private int id;
    private int jobId;
    private int candidateId;
    private List<String> resumeSkills;
    private String coverLetter;
    private ApplicationStatus status;
    private double score;
    private LocalDate appliedDate;

    public JobApplication(int id, int jobId, int candidateId, List<String> resumeSkills, String coverLetter,
                           ApplicationStatus status, double score, LocalDate appliedDate) {
        this.id = id;
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.resumeSkills = resumeSkills;
        this.coverLetter = coverLetter;
        this.status = status;
        this.score = score;
        this.appliedDate = appliedDate;
    }

    public int getId() { return id; }
    public int getJobId() { return jobId; }
    public int getCandidateId() { return candidateId; }
    public List<String> getResumeSkills() { return resumeSkills; }
    public String getCoverLetter() { return coverLetter; }
    public ApplicationStatus getStatus() { return status; }
    public double getScore() { return score; }
    public LocalDate getAppliedDate() { return appliedDate; }

    public void setStatus(ApplicationStatus status) { this.status = status; }

    public String toCsv() {
        String letter = CsvUtil.sanitize(coverLetter);
        return id + "|" + jobId + "|" + candidateId + "|" + CsvUtil.joinSkills(resumeSkills) + "|"
                + letter + "|" + status + "|" + score + "|" + appliedDate;
    }

    public static JobApplication fromCsv(String line) {
        String[] p = line.split("\\|", -1);
        List<String> skills = p[3].isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(p[3].split(";")));
        return new JobApplication(
                Integer.parseInt(p[0]),
                Integer.parseInt(p[1]),
                Integer.parseInt(p[2]),
                skills,
                p[4],
                ApplicationStatus.valueOf(p[5]),
                Double.parseDouble(p[6]),
                LocalDate.parse(p[7])
        );
    }

    @Override
    public String toString() {
        return "#" + id + " Job#" + jobId + " Candidate#" + candidateId
                + " Score=" + String.format("%.1f", score) + "% [" + status + "] on " + appliedDate;
    }
}
