package com.smarthire.service;

import com.smarthire.model.*;
import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.InterviewRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.repository.StatusHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;

public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final StatusHistoryRepository statusHistoryRepository;

    public InterviewService(InterviewRepository interviewRepository, ApplicationRepository applicationRepository,
                             JobRepository jobRepository, StatusHistoryRepository statusHistoryRepository) {
        this.interviewRepository = interviewRepository;
        this.applicationRepository = applicationRepository;
        this.jobRepository = jobRepository;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    public Interview schedule(User actor, int applicationId, LocalDateTime when, String interviewer, InterviewMode mode) {
        JobApplication app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new SmartHireException("Application #" + applicationId + " not found."));
        Job job = jobRepository.findById(app.getJobId()).orElseThrow(() -> new SmartHireException("Job not found."));
        requireOwnerOrAdmin(actor, job.getRecruiterId());

        Interview interview = new Interview(interviewRepository.nextId(), applicationId, when, interviewer,
                mode, InterviewStatus.SCHEDULED, "");
        interviewRepository.save(interview);

        ApplicationStatus previous = app.getStatus();
        app.setStatus(ApplicationStatus.INTERVIEW_SCHEDULED);
        applicationRepository.save(app);
        recordTransition(applicationId, previous, ApplicationStatus.INTERVIEW_SCHEDULED, actor.getId(),
                "Interview scheduled with " + interviewer);
        return interview;
    }

    public void complete(User actor, int interviewId, String feedback, boolean hire) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new SmartHireException("Interview #" + interviewId + " not found."));
        JobApplication app = applicationRepository.findById(interview.getApplicationId())
                .orElseThrow(() -> new SmartHireException("Application not found."));
        Job job = jobRepository.findById(app.getJobId()).orElseThrow(() -> new SmartHireException("Job not found."));
        requireOwnerOrAdmin(actor, job.getRecruiterId());

        interview.setStatus(InterviewStatus.COMPLETED);
        interview.setFeedback(feedback);
        interviewRepository.save(interview);

        ApplicationStatus previous = app.getStatus();
        ApplicationStatus outcome = hire ? ApplicationStatus.HIRED : ApplicationStatus.REJECTED;
        app.setStatus(outcome);
        applicationRepository.save(app);
        recordTransition(app.getId(), previous, outcome, actor.getId(), "Interview outcome: " + feedback);
    }

    public void cancel(User actor, int interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new SmartHireException("Interview #" + interviewId + " not found."));
        interview.setStatus(InterviewStatus.CANCELLED);
        interviewRepository.save(interview);
    }

    public List<Interview> listForApplication(int applicationId) {
        return interviewRepository.findByApplication(applicationId);
    }

    public List<Interview> listAll() {
        return interviewRepository.findAll();
    }

    private void requireOwnerOrAdmin(User actor, int ownerId) {
        if (actor.getRole() != UserRole.ADMIN && actor.getId() != ownerId) {
            throw new SmartHireException("You do not have permission to manage this interview.");
        }
    }

    private void recordTransition(int applicationId, ApplicationStatus from, ApplicationStatus to, int actorUserId, String note) {
        statusHistoryRepository.append(new StatusHistoryEntry(statusHistoryRepository.nextId(), applicationId,
                from, to, actorUserId, LocalDateTime.now(), note));
    }
}
