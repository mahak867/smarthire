package com.smarthire.model;

import com.smarthire.util.CsvUtil;

import java.time.LocalDateTime;

/**
 * Records one status transition of a JobApplication over time, so the full
 * history (not just the current status) can be inspected. Mirrors the
 * ApplicationStatusHistory entity of the original SmartHire project.
 */
public class StatusHistoryEntry {

    private int id;
    private int applicationId;
    private ApplicationStatus fromStatus;
    private ApplicationStatus toStatus;
    private int changedByUserId;
    private LocalDateTime changedAt;
    private String note;

    public StatusHistoryEntry(int id, int applicationId, ApplicationStatus fromStatus, ApplicationStatus toStatus,
                               int changedByUserId, LocalDateTime changedAt, String note) {
        this.id = id;
        this.applicationId = applicationId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedByUserId = changedByUserId;
        this.changedAt = changedAt;
        this.note = note;
    }

    public int getId() { return id; }
    public int getApplicationId() { return applicationId; }
    public ApplicationStatus getFromStatus() { return fromStatus; }
    public ApplicationStatus getToStatus() { return toStatus; }
    public int getChangedByUserId() { return changedByUserId; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public String getNote() { return note; }

    public String toCsv() {
        String n = CsvUtil.sanitize(note == null ? "" : note);
        return id + "|" + applicationId + "|" + fromStatus + "|" + toStatus + "|"
                + changedByUserId + "|" + changedAt + "|" + n;
    }

    public static StatusHistoryEntry fromCsv(String line) {
        String[] p = line.split("\\|", -1);
        return new StatusHistoryEntry(
                Integer.parseInt(p[0]),
                Integer.parseInt(p[1]),
                ApplicationStatus.valueOf(p[2]),
                ApplicationStatus.valueOf(p[3]),
                Integer.parseInt(p[4]),
                LocalDateTime.parse(p[5]),
                p[6]
        );
    }

    @Override
    public String toString() {
        return changedAt + "  " + fromStatus + " -> " + toStatus
                + " (by user #" + changedByUserId + ")"
                + (note != null && !note.isEmpty() ? "  [" + note + "]" : "");
    }
}
