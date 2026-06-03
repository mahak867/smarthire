// ── SmartHire · src/main/java/com/smarthire/service/ApplicationService.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.request.*;
import com.smarthire.api.v1.dto.response.*;
import com.smarthire.domain.entities.*;
import com.smarthire.domain.enums.*;
import com.smarthire.domain.repositories.*;
import com.smarthire.exception.*;
import com.smarthire.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final JobRepository         jobRepository;
    private final UserRepository        userRepository;
    private final AiScoringService      aiScoringService;
    private final EmailService                     emailService;
    private final ApplicationStatusHistoryRepository historyRepository;
    private final NotificationService notificationService;
    private final AuditLogger           auditLogger;

    @Transactional
    public ApplicationResponse submit(SubmitApplicationRequest request, UUID candidateId) {
        Job job = jobRepository.findById(request.jobId())
            .orElseThrow(() -> new NotFoundException(
                "Job not found. It may have been removed or the ID is incorrect."));

        if (job.getStatus() != JobStatus.OPEN) {
            throw new BadRequestException(
                "This job posting is no longer accepting applications (status: " + job.getStatus() + ").");
        }

        if (applicationRepository.existsByJobIdAndCandidateId(request.jobId(), candidateId)) {
            throw new ConflictException("You have already applied for this position.");
        }

        User candidate = userRepository.findById(candidateId)
            .orElseThrow(() -> new NotFoundException("Candidate account not found."));

        Application app = Application.builder()
            .job(job)
            .candidate(candidate)
            .resumeUrl(request.resumeUrl())
            .coverLetter(request.coverLetter())
            .status(ApplicationStatus.APPLIED)
            .scoringComplete(false)
            .build();

        app = applicationRepository.save(app);

        // Trigger async AI scoring — non-blocking
        final UUID appId = app.getId();
        aiScoringService.scoreApplicationAsync(appId);

        log.info("Application submitted: {} for job {}", appId, request.jobId());
        return toResponse(app);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ApplicationResponse> list(UUID jobId, String status,
                                                    Double minScore, Pageable pageable) {
        ApplicationStatus statusEnum = null;
        if (status != null) {
            try { statusEnum = ApplicationStatus.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid status: '" + status +
                    "'. Valid values: APPLIED, SCREENING, SHORTLISTED, INTERVIEW, OFFERED, REJECTED, WITHDRAWN");
            }
        }
        BigDecimal minScoreDec = minScore != null ? BigDecimal.valueOf(minScore) : null;
        Page<Application> page = applicationRepository.findFiltered(jobId, statusEnum, minScoreDec, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(UUID appId, UUID requestingUserId) {
        Application app = applicationRepository.findById(appId)
            .orElseThrow(() -> new NotFoundException("Application not found."));

        User requester = userRepository.findById(requestingUserId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        boolean isCandidate = requester.getRole() == UserRole.CANDIDATE;
        if (isCandidate && !app.getCandidate().getId().equals(requestingUserId)) {
            throw new ForbiddenException("You do not have permission to view this application.");
        }
        return toResponse(app);
    }

    @Transactional
    public ApplicationResponse updateStatus(UUID appId, UpdateApplicationStatusRequest request, UUID updatedBy) {
        Application app = applicationRepository.findById(appId)
            .orElseThrow(() -> new NotFoundException("Application not found."));

        ApplicationStatus newStatus;
        try { newStatus = ApplicationStatus.valueOf(request.status().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid application status: '" + request.status() + "'.");
        }

        ApplicationStatus oldStatus = app.getStatus();
        ApplicationStatus previousStatus = app.getStatus();
        app.setStatus(newStatus);

        // Record immutable status change history
        historyRepository.save(ApplicationStatusHistory.builder()
            .application(app)
            .fromStatus(previousStatus)
            .toStatus(newStatus)
            .changedBy(updatedBy)
            .changedByName(request.note() != null ? null : null)  // resolved below
            .note(request.note())
            .build());
        if (request.notes() != null) app.setNotes(request.notes());
        app = applicationRepository.save(app);

        auditLogger.log(updatedBy, "APPLICATION_STATUS_CHANGED", "APPLICATION", appId, null, null,
            Map.of("from", oldStatus.name(), "to", newStatus.name()));

        // Send email notification to candidate asynchronously
        emailService.sendApplicationStatusUpdate(app);

        return toResponse(app);
    }

    @Transactional(readOnly = true)
    public PagedResponse<ApplicationResponse> getByCandidate(UUID candidateId, Pageable pageable) {
        Page<Application> page = applicationRepository.findByCandidateId(candidateId, pageable);
        return toPagedResponse(page);
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getRankedByJob(UUID jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new NotFoundException("Job not found.");
        }
        return applicationRepository.findByJobIdOrderByAiScoreDescUnpaged(jobId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ApplicationResponse toResponse(Application a) {
        return new ApplicationResponse(
            a.getId(),
            a.getJob().getId(),
            a.getJob().getTitle(),
            a.getCandidate().getId(),
            a.getCandidate().getFullName(),
            a.getCandidate().getEmail(),
            a.getResumeUrl(),
            a.getCoverLetter(),
            a.getStatus().name(),
            a.getAiScore(),
            a.getAiSummary(),
            a.getSkillMatchPct(),
            a.getKeywordMatches(),
            a.isScoringComplete(),
            a.getNotes(),
            a.getAppliedAt(),
            a.getUpdatedAt()
        );
    }

    private PagedResponse<ApplicationResponse> toPagedResponse(Page<Application> page) {
        List<ApplicationResponse> content = page.getContent().stream()
            .map(this::toResponse).collect(Collectors.toList());
        return new PagedResponse<>(content, page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /**
     * Bulk status update — processes up to 200 applications in a single transaction.
     * Skips applications in terminal states (REJECTED, WITHDRAWN) unless admin.
     * Returns count of successfully updated applications.
     */
    @Transactional
    public int bulkUpdateStatus(List<UUID> ids, ApplicationStatus newStatus, String note, UUID updatedBy) {
        int count = 0;
        for (UUID id : ids) {
            try {
                var app = applicationRepository.findById(id).orElse(null);
                if (app == null) continue;
                // Skip terminal states
                if (app.getStatus() == ApplicationStatus.REJECTED ||
                    app.getStatus() == ApplicationStatus.WITHDRAWN) continue;

                ApplicationStatus prev = app.getStatus();
                app.setStatus(newStatus);
                applicationRepository.save(app);

                historyRepository.save(ApplicationStatusHistory.builder()
                    .application(app).fromStatus(prev).toStatus(newStatus)
                    .changedBy(updatedBy).note(note != null ? note : "Bulk update").build());

                notificationService.pushUserNotification(
                    app.getCandidate().getId(),
                    "STATUS_CHANGE",
                    "Application Update",
                    "Your application for " + app.getJob().getTitle() + " has been moved to " + newStatus.name().toLowerCase().replace("_", " ") + "."
                );
                count++;
            } catch (Exception e) {
                log.warn("Bulk status update failed for application {}: {}", id, e.getMessage());
            }
        }
        auditLogger.log(updatedBy, "BULK_STATUS_UPDATE", "Application", null,
            "count=" + count + " status=" + newStatus);
        return count;
    }

}