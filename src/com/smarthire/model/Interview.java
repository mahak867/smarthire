package com.smarthire.model;

import com.smarthire.util.CsvUtil;

import java.time.LocalDateTime;

/**
 * Represents a scheduled/completed interview linked to a JobApplication.
 */
public class Interview {

    private int id;
    private int applicationId;
    private LocalDateTime scheduledAt;
    private String interviewer;
    private InterviewMode mode;
    private InterviewStatus status;
    private String feedback;

    public Interview(int id, int applicationId, LocalDateTime scheduledAt, String interviewer,
                      InterviewMode mode, InterviewStatus status, String feedback) {
        this.id = id;
        this.applicationId = applicationId;
        this.scheduledAt = scheduledAt;
        this.interviewer = interviewer;
        this.mode = mode;
        this.status = status;
        this.feedback = feedback;
    }

    public int getId() { return id; }
    public int getApplicationId() { return applicationId; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getInterviewer() { return interviewer; }
    public InterviewMode getMode() { return mode; }
    public InterviewStatus getStatus() { return status; }
    public String getFeedback() { return feedback; }

    public void setStatus(InterviewStatus status) { this.status = status; }
    public void setFeedback(String feedback) { this.feedback = feedback; }

    public String toCsv() {
        String fb = CsvUtil.sanitize(feedback == null ? "" : feedback);
        String safeInterviewer = CsvUtil.sanitize(interviewer);
        return id + "|" + applicationId + "|" + scheduledAt + "|" + safeInterviewer + "|"
                + mode + "|" + status + "|" + fb;
    }

    public static Interview fromCsv(String line) {
        String[] p = line.split("\\|", -1);
        return new Interview(
                Integer.parseInt(p[0]),
                Integer.parseInt(p[1]),
                LocalDateTime.parse(p[2]),
                p[3],
                InterviewMode.valueOf(p[4]),
                InterviewStatus.valueOf(p[5]),
                p[6]
        );
    }

    @Override
    public String toString() {
        return "#" + id + " for Application#" + applicationId + " with " + interviewer
                + " (" + mode + ") at " + scheduledAt + " [" + status + "]"
                + (feedback != null && !feedback.isEmpty() ? "\n    Feedback: " + feedback : "");
    }
}
