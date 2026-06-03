// ── SmartHire · service/ExportService.java ──
package com.smarthire.service;

import com.smarthire.domain.enums.ApplicationStatus;
import com.smarthire.domain.enums.JobStatus;
import com.smarthire.domain.repositories.ApplicationRepository;
import com.smarthire.domain.repositories.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository         jobRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_ROWS = 10_000;

    /**
     * Export all applications (optionally filtered by jobId or status) as RFC 4180 CSV.
     * Returns the CSV as a String — the controller sets Content-Disposition: attachment.
     */
    @Transactional(readOnly = true)
    public String exportApplicationsCsv(UUID jobId, ApplicationStatus status) {
        var pageable = PageRequest.of(0, MAX_ROWS, Sort.by("appliedAt").descending());

        var applications = jobId != null
            ? applicationRepository.findByJobIdOrderByAiScoreDesc(jobId, pageable).getContent()
            : applicationRepository.findFiltered(status, null, null, null, pageable).getContent();

        StringWriter sw = new StringWriter();

        // Header
        sw.append("ID,Candidate Name,Candidate Email,Job Title,Department,");
        sw.append("Status,AI Score,Skill Match %,Recommendation,Cover Letter,Applied At,Updated At\n");

        for (var app : applications) {
            String candidateName  = csv(app.getCandidate().getFirstName() + " " + app.getCandidate().getLastName());
            String candidateEmail = csv(app.getCandidate().getEmail());
            String jobTitle       = csv(app.getJob().getTitle());
            String department     = csv(app.getJob().getDepartment());
            String appStatus      = app.getStatus().name();
            String aiScore        = app.getAiScore() != null ? app.getAiScore().toPlainString() : "";
            String skillMatch     = app.getSkillMatchPct() != null ? app.getSkillMatchPct().toPlainString() : "";
            String recommendation = "";
            if (app.getKeywordMatches() != null && app.getKeywordMatches().containsKey("recommendation")) {
                recommendation = app.getKeywordMatches().get("recommendation").toString();
            }
            String coverLetter   = csv(app.getCoverLetter());
            String appliedAt     = app.getAppliedAt() != null ? app.getAppliedAt().format(FMT) : "";
            String updatedAt     = app.getUpdatedAt() != null ? app.getUpdatedAt().format(FMT) : "";

            sw.append(String.join(",",
                app.getId().toString(), candidateName, candidateEmail,
                jobTitle, department, appStatus, aiScore, skillMatch,
                recommendation, coverLetter, appliedAt, updatedAt
            )).append("\n");
        }

        log.info("CSV export: {} rows", applications.size());
        return sw.toString();
    }

    /**
     * Export job pipeline summary as CSV — one row per job with counts per stage.
     */
    @Transactional(readOnly = true)
    public String exportPipelineCsv() {
        var jobs = jobRepository.findFiltered(JobStatus.OPEN, null, null, null,
            PageRequest.of(0, 500, Sort.by("createdAt").descending())).getContent();

        StringWriter sw = new StringWriter();
        sw.append("Job ID,Title,Department,Location,Posted By,Applied,Screening,Shortlisted,Interview,Offered,Rejected,Total,Avg AI Score\n");

        for (var job : jobs) {
            var apps = applicationRepository.findByJobIdOrderByAiScoreDescUnpaged(job.getId());

            long applied      = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.APPLIED).count();
            long screening    = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.SCREENING).count();
            long shortlisted  = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.SHORTLISTED).count();
            long interview    = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW).count();
            long offered      = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.OFFERED).count();
            long rejected     = apps.stream().filter(a -> a.getStatus() == ApplicationStatus.REJECTED).count();
            double avgScore   = apps.stream()
                .filter(a -> a.getAiScore() != null)
                .mapToDouble(a -> a.getAiScore().doubleValue())
                .average().orElse(0.0);

            sw.append(String.join(",",
                job.getId().toString(), csv(job.getTitle()), csv(job.getDepartment()),
                csv(job.getLocation()),
                csv(job.getPostedBy() != null ? job.getPostedBy().getFullName() : ""),
                String.valueOf(applied), String.valueOf(screening), String.valueOf(shortlisted),
                String.valueOf(interview), String.valueOf(offered), String.valueOf(rejected),
                String.valueOf(apps.size()), String.format("%.1f", avgScore)
            )).append("\n");
        }
        return sw.toString();
    }

    /** Escapes a field for CSV: wraps in quotes if it contains comma, quote, or newline. */
    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
