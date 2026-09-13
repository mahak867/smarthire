package com.smarthire.service;

import com.smarthire.model.*;
import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.InterviewRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.repository.StatusHistoryRepository;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardService {

    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public DashboardService(JobRepository jobRepository, ApplicationRepository applicationRepository,
                             InterviewRepository interviewRepository, StatusHistoryRepository statusHistoryRepository) {
        this.jobRepository = jobRepository;
        this.applicationRepository = applicationRepository;
        this.interviewRepository = interviewRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    /** Builds a printable dashboard, optionally scoped to a single recruiter's jobs (pass -1 for all jobs). */
    public String buildReport(int recruiterIdOrAll) {
        List<Job> jobs = recruiterIdOrAll == -1
                ? jobRepository.findAll()
                : jobRepository.findByRecruiter(recruiterIdOrAll);

        List<Integer> jobIds = jobs.stream().map(Job::getId).collect(Collectors.toList());
        List<JobApplication> apps = applicationRepository.findAll().stream()
                .filter(a -> jobIds.contains(a.getJobId()))
                .collect(Collectors.toList());
        List<Interview> interviews = interviewRepository.findAll().stream()
                .filter(i -> apps.stream().anyMatch(a -> a.getId() == i.getApplicationId()))
                .collect(Collectors.toList());

        long openJobs = jobs.stream().filter(j -> j.getStatus() == JobStatus.OPEN).count();
        Map<ApplicationStatus, Long> byStatus = apps.stream()
                .collect(Collectors.groupingBy(JobApplication::getStatus, Collectors.counting()));
        double avgScore = apps.stream().mapToDouble(JobApplication::getScore).average().orElse(0.0);
        long hired = byStatus.getOrDefault(ApplicationStatus.HIRED, 0L);
        long total = apps.size();

        StringBuilder sb = new StringBuilder();
        sb.append("Total jobs posted     : ").append(jobs.size()).append("\n");
        sb.append("Open jobs              : ").append(openJobs).append("\n");
        sb.append("Total applications     : ").append(total).append("\n");
        sb.append("Average resume score   : ").append(String.format("%.1f%%", avgScore)).append("\n");
        sb.append("Interviews scheduled   : ").append(interviews.size()).append("\n");
        sb.append("Candidates hired       : ").append(hired).append("\n");

        sb.append("\nApplications by status:\n");
        for (ApplicationStatus status : ApplicationStatus.values()) {
            sb.append("  ").append(String.format("%-22s", status)).append(": ")
                    .append(byStatus.getOrDefault(status, 0L)).append("\n");
        }

        sb.append("\nHiring funnel (% of total applications that reached this stage):\n");
        sb.append(funnelLine("Applied", total, total));
        long shortlistedOrLater = countReached(apps, ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW_SCHEDULED,
                ApplicationStatus.HIRED, ApplicationStatus.REJECTED);
        long interviewedOrLater = countReached(apps, ApplicationStatus.INTERVIEW_SCHEDULED, ApplicationStatus.HIRED,
                ApplicationStatus.REJECTED);
        sb.append(funnelLine("Shortlisted", shortlistedOrLater, total));
        sb.append(funnelLine("Interviewed", interviewedOrLater, total));
        sb.append(funnelLine("Hired", hired, total));

        double avgDaysToHire = averageDaysToHire(apps);
        sb.append("\nAverage time to hire   : ")
                .append(avgDaysToHire >= 0 ? String.format("%.1f days", avgDaysToHire) : "n/a (no hires yet)")
                .append("\n");

        return sb.toString();
    }

    private long countReached(List<JobApplication> apps, ApplicationStatus... anyOf) {
        java.util.Set<ApplicationStatus> targets = java.util.Set.of(anyOf);
        return apps.stream().filter(a -> targets.contains(a.getStatus())).count();
    }

    private String funnelLine(String label, long count, long total) {
        double pct = total == 0 ? 0.0 : (count * 100.0) / total;
        return String.format("  %-14s: %4d  (%.1f%%)%n", label, count, pct);
    }

    /**
     * Computes the average number of days between an application's "APPLIED"
     * transition and its "HIRED" transition, using the status-history log
     * (java.time.temporal.ChronoUnit) rather than just current status.
     */
    private double averageDaysToHire(List<JobApplication> apps) {
        List<Long> daysList = apps.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.HIRED)
                .map(a -> statusHistoryRepository.findByApplication(a.getId()))
                .filter(history -> !history.isEmpty())
                .map(history -> {
                    var applied = history.get(0).getChangedAt();
                    var hiredAt = history.get(history.size() - 1).getChangedAt();
                    return ChronoUnit.DAYS.between(applied, hiredAt);
                })
                .collect(Collectors.toList());
        return daysList.isEmpty() ? -1.0 : daysList.stream().mapToLong(Long::longValue).average().orElse(-1.0);
    }
}
