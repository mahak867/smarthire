// ── SmartHire · src/main/java/com/smarthire/service/InterviewService.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.request.ScheduleInterviewRequest;
import com.smarthire.api.v1.dto.response.InterviewResponse;
import com.smarthire.domain.entities.Application;
import com.smarthire.domain.entities.Interview;
import com.smarthire.domain.entities.User;
import com.smarthire.domain.enums.ApplicationStatus;
import com.smarthire.domain.enums.InterviewStatus;
import com.smarthire.domain.repositories.ApplicationRepository;
import com.smarthire.domain.repositories.InterviewRepository;
import com.smarthire.domain.repositories.UserRepository;
import com.smarthire.exception.BadRequestException;
import com.smarthire.exception.ForbiddenException;
import com.smarthire.exception.NotFoundException;
import com.smarthire.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InterviewService {

    private final InterviewRepository   interviewRepository;
    private final ApplicationRepository applicationRepository;
    private final UserRepository        userRepository;
    private final EmailService          emailService;
    private final AuditLogger           auditLogger;

    @Transactional
    public InterviewResponse schedule(ScheduleInterviewRequest req, UUID scheduledById) {
        Application application = applicationRepository.findById(req.applicationId())
            .orElseThrow(() -> new NotFoundException("Application not found."));

        if (application.getStatus() == ApplicationStatus.REJECTED ||
            application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw new BadRequestException(
                "Cannot schedule an interview for a " + application.getStatus().name().toLowerCase() + " application.");
        }

        if (req.scheduledAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Interview must be scheduled in the future.");
        }

        User interviewer = userRepository.findById(req.interviewerId())
            .orElseThrow(() -> new NotFoundException("Interviewer account not found."));

        Interview interview = Interview.builder()
            .application(application)
            .scheduledAt(req.scheduledAt())
            .durationMinutes(req.durationMinutes() != null ? req.durationMinutes() : 60)
            .type(req.type())
            .interviewer(interviewer)
            .status(InterviewStatus.SCHEDULED)
            .meetingLink(req.meetingLink())
            .notes(req.notes())
            .build();

        interview = interviewRepository.save(interview);

        // Move application to INTERVIEW status
        application.setStatus(ApplicationStatus.INTERVIEW);
        applicationRepository.save(application);

        // Notify candidate
        emailService.sendInterviewScheduled(
            application.getCandidate(),
            application.getJob().getTitle(),
            interview.getScheduledAt(),
            interview.getType().name(),
            interview.getMeetingLink());

        auditLogger.log(scheduledById, "INTERVIEW_SCHEDULED", "Interview", interview.getId(), null);
        log.info("Interview scheduled: {} for application {}", interview.getId(), req.applicationId());
        return toResponse(interview);
    }

    @Transactional
    public InterviewResponse submitFeedback(UUID interviewId, String feedback, Integer rating, UUID submittedById) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new NotFoundException("Interview not found."));

        if (!interview.getInterviewer().getId().equals(submittedById)) {
            throw new ForbiddenException("Only the assigned interviewer can submit feedback.");
        }
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new BadRequestException("Rating must be between 1 and 5.");
        }

        interview.setFeedback(feedback);
        interview.setRating(rating);
        interview.setStatus(InterviewStatus.COMPLETED);
        interview = interviewRepository.save(interview);

        auditLogger.log(submittedById, "INTERVIEW_FEEDBACK_SUBMITTED", "Interview", interviewId, null);
        return toResponse(interview);
    }

    @Transactional
    public InterviewResponse cancel(UUID interviewId, UUID requestedById, boolean isAdmin) {
        Interview interview = interviewRepository.findById(interviewId)
            .orElseThrow(() -> new NotFoundException("Interview not found."));

        boolean isInterviewer = interview.getInterviewer().getId().equals(requestedById);
        boolean isRecruiter   = interview.getApplication().getJob().getPostedBy().getId().equals(requestedById);

        if (!isAdmin && !isInterviewer && !isRecruiter) {
            throw new ForbiddenException("You do not have permission to cancel this interview.");
        }
        if (interview.getStatus() == InterviewStatus.COMPLETED) {
            throw new BadRequestException("A completed interview cannot be cancelled.");
        }

        interview.setStatus(InterviewStatus.CANCELLED);
        interview = interviewRepository.save(interview);

        emailService.sendInterviewCancelled(
            interview.getApplication().getCandidate(),
            interview.getApplication().getJob().getTitle(),
            interview.getScheduledAt());

        auditLogger.log(requestedById, "INTERVIEW_CANCELLED", "Interview", interviewId, null);
        return toResponse(interview);
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> findByApplication(UUID applicationId) {
        return interviewRepository.findByApplicationIdOrderByScheduledAtDesc(applicationId)
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InterviewResponse> findByInterviewer(UUID interviewerId) {
        return interviewRepository.findByInterviewerIdAndStatus(interviewerId, InterviewStatus.SCHEDULED)
            .stream().map(this::toResponse).toList();
    }

    public InterviewResponse toResponse(Interview i) {
        return new InterviewResponse(
            i.getId(),
            i.getApplication().getId(),
            i.getApplication().getCandidate().getFirstName() + " " + i.getApplication().getCandidate().getLastName(),
            i.getApplication().getJob().getTitle(),
            i.getScheduledAt(),
            i.getDurationMinutes(),
            i.getType(),
            i.getStatus(),
            i.getInterviewer().getId(),
            i.getInterviewer().getFirstName() + " " + i.getInterviewer().getLastName(),
            i.getMeetingLink(),
            i.getNotes(),
            i.getFeedback(),
            i.getRating(),
            i.getCreatedAt()
        );
    }
}
