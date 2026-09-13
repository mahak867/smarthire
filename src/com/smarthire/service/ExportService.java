package com.smarthire.service;

import com.smarthire.model.Job;
import com.smarthire.model.JobApplication;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Writes plain-text reports to a reports/ folder using java.io.PrintWriter.
 * Stands in for the original project's ExportService (which produced CSV/PDF
 * downloads over HTTP) - here the "export" is simply a file written to disk,
 * which fits a terminal application.
 */
public class ExportService {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private final Path reportsDir;

    public ExportService(String dataDir) {
        this.reportsDir = Paths.get(dataDir, "reports");
    }

    public String exportDashboard(String reportText) {
        String filename = "dashboard_" + LocalDateTime.now().format(FILE_TS) + ".txt";
        return write(filename, reportText);
    }

    public String exportApplicationsForJob(Job job, List<JobApplication> apps, Map<Integer, String> candidateNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("Applications report for Job #").append(job.getId()).append(" - ").append(job.getTitle()).append("\n");
        sb.append("Generated: ").append(LocalDateTime.now()).append("\n");
        sb.append("=".repeat(60)).append("\n\n");
        for (JobApplication a : apps) {
            String candidateName = candidateNames != null ? candidateNames.getOrDefault(a.getCandidateId(), "Unknown") : null;
            sb.append("Application #").append(a.getId()).append("\n");
            sb.append("  Candidate    : ").append(candidateName != null ? candidateName + " (#" + a.getCandidateId() + ")" : "#" + a.getCandidateId()).append("\n");
            sb.append("  Match score  : ").append(String.format("%.1f%%", a.getScore())).append("\n");
            sb.append("  Status       : ").append(a.getStatus()).append("\n");
            sb.append("  Skills       : ").append(String.join(", ", a.getResumeSkills())).append("\n");
            sb.append("  Cover letter : ").append(a.getCoverLetter()).append("\n");
            sb.append("  Applied on   : ").append(a.getAppliedDate()).append("\n\n");
        }
        String filename = "applications_job" + job.getId() + "_" + LocalDateTime.now().format(FILE_TS) + ".txt";
        return write(filename, sb.toString());
    }

    private String write(String filename, String content) {
        try {
            Files.createDirectories(reportsDir);
            Path target = reportsDir.resolve(filename);
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(target))) {
                pw.print(content);
            }
            return target.toString();
        } catch (IOException e) {
            throw new SmartHireException("Could not write report: " + e.getMessage());
        }
    }
}
