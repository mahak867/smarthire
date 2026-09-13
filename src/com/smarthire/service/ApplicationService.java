package com.smarthire.service;

import com.smarthire.model.*;
import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.repository.StatusHistoryRepository;
import com.smarthire.util.Sorter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final ScoringService scoringService;

    public ApplicationService(ApplicationRepository applicationRepository, JobRepository jobRepository,
                               StatusHistoryRepository statusHistoryRepository, ScoringService scoringService) {
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.scoringService = scoringService;
    }

    public JobApplication apply(User candidate, int jobId, List<String> resumeSkills, String coverLetter) {
        if (candidate.getRole() != UserRole.CANDIDATE) {
            throw new SmartHireException("Only candidates can apply to jobs.");
        }
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new SmartHireException("Job #" + jobId + " not found."));
        if (job.getStatus() != JobStatus.OPEN) {
            throw new SmartHireException("This job is closed and no longer accepting applications.");
        }
        if (applicationRepository.existsByJobAndCandidate(jobId, candidate.getId())) {
            throw new SmartHireException("You have already applied to this job.");
        }

        double score = scoringService.computeScore(resumeSkills, job.getRequiredSkills());
        JobApplication app = new JobApplication(applicationRepository.nextId(), jobId, candidate.getId(),
                resumeSkills, coverLetter, ApplicationStatus.APPLIED, score, LocalDate.now());
        applicationRepository.save(app);
        recordTransition(app.getId(), ApplicationStatus.APPLIED, ApplicationStatus.APPLIED, candidate.getId(), "Application submitted");
        return app;
    }

    public void updateStatus(User actor, int applicationId, ApplicationStatus newStatus) {
        JobApplication app = getById(applicationId);
        Job job = jobRepository.findById(app.getJobId()).orElseThrow(() -> new SmartHireException("Job not found."));
        if (actor.getRole() != UserRole.ADMIN && actor.getId() != job.getRecruiterId()) {
            throw new SmartHireException("You do not have permission to update this application.");
        }
        ApplicationStatus previous = app.getStatus();
        app.setStatus(newStatus);
        applicationRepository.save(app);
        recordTransition(applicationId, previous, newStatus, actor.getId(), "Manual status update");
    }

    public List<JobApplication> shortlistTop(User actor, int jobId, int n) {
        Job job = jobRepository.findById(jobId).orElseThrow(() -> new SmartHireException("Job #" + jobId + " not found."));
        if (actor.getRole() != UserRole.ADMIN && actor.getId() != job.getRecruiterId()) {
            throw new SmartHireException("You do not have permission to shortlist for this job.");
        }
        if (n <= 0) {
            throw new SmartHireException("Number of candidates to shortlist must be a positive number.");
        }
        List<JobApplication> ranked = Sorter.mergeSort(applicationRepository.findByJob(jobId),
                        Comparator.comparingDouble(JobApplication::getScore).reversed())
                .stream()
                .limit(n)
                .collect(Collectors.toList());
        for (JobApplication a : ranked) {
            ApplicationStatus previous = a.getStatus();
            a.setStatus(ApplicationStatus.SHORTLISTED);
            applicationRepository.save(a);
            recordTransition(a.getId(), previous, ApplicationStatus.SHORTLISTED, actor.getId(), "Auto-shortlisted (top " + n + " by score)");
        }
        return ranked;
    }

    public JobApplication getById(int id) {
        return applicationRepository.findById(id).orElseThrow(() -> new SmartHireException("Application #" + id + " not found."));
    }

    public List<JobApplication> listForJob(int jobId) {
        return Sorter.mergeSort(applicationRepository.findByJob(jobId),
                Comparator.comparingDouble(JobApplication::getScore).reversed());
    }

    public List<JobApplication> listForCandidate(int candidateId) {
        return applicationRepository.findByCandidate(candidateId);
    }

    public List<JobApplication> listAll() {
        return applicationRepository.findAll();
    }

    /** Filters a job's applications down to a single status, still ranked by score. */
    public List<JobApplication> listForJobByStatus(int jobId, ApplicationStatus status) {
        return listForJob(jobId).stream()
                .filter(a -> a.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<StatusHistoryEntry> getHistory(int applicationId) {
        return statusHistoryRepository.findByApplication(applicationId);
    }

    /** Package-visible so InterviewService can log transitions it causes (schedule/complete) through the same trail. */
    void recordTransition(int applicationId, ApplicationStatus from, ApplicationStatus to, int actorUserId, String note) {
        statusHistoryRepository.append(new StatusHistoryEntry(statusHistoryRepository.nextId(), applicationId,
                from, to, actorUserId, LocalDateTime.now(), note));
    }
}
