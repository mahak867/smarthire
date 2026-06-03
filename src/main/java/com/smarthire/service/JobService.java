// ── SmartHire · src/main/java/com/smarthire/service/JobService.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.request.CreateJobRequest;
import com.smarthire.api.v1.dto.request.UpdateJobRequest;
import com.smarthire.api.v1.dto.response.JobResponse;
import com.smarthire.api.v1.dto.response.PagedResponse;
import com.smarthire.domain.entities.Job;
import com.smarthire.domain.entities.User;
import com.smarthire.domain.enums.JobStatus;
import com.smarthire.domain.repositories.ApplicationRepository;
import com.smarthire.domain.repositories.JobRepository;
import com.smarthire.domain.repositories.UserRepository;
import com.smarthire.exception.ForbiddenException;
import com.smarthire.exception.NotFoundException;
import com.smarthire.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository       jobRepository;
    private final UserRepository      userRepository;
    private final ApplicationRepository applicationRepository;
    private final StringRedisTemplate redisTemplate;
    private final AuditLogger         auditLogger;

    private static final String VIEWS_KEY = "job:views:";

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public JobResponse create(CreateJobRequest req, UUID postedById) {
        User poster = userRepository.findById(postedById)
            .orElseThrow(() -> new NotFoundException("Recruiter account not found."));

        Job job = Job.builder()
            .title(req.title().strip())
            .description(req.description().strip())
            .requirements(req.requirements().strip())
            .department(req.department() != null ? req.department().strip() : null)
            .location(req.location() != null ? req.location().strip() : null)
            .employmentType(req.employmentType())
            .salaryMin(req.salaryMin())
            .salaryMax(req.salaryMax())
            .currency(req.currency() != null ? req.currency() : "INR")
            .status(req.publishNow() != null && req.publishNow() ? JobStatus.OPEN : JobStatus.DRAFT)
            .deadline(req.deadline())
            .postedBy(poster)
            .viewsCount(0L)
            .build();

        job = jobRepository.save(job);
        auditLogger.log(postedById, "JOB_CREATED", "Job", job.getId(), null);
        log.info("Job created: {} by user {}", job.getId(), postedById);
        return toResponse(job, 0L);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    @org.springframework.cache.annotation.Cacheable(value = "job-detail", key = "#jobId", condition = "!#incrementViews")
    public JobResponse findById(UUID jobId, boolean incrementViews) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException(
                "Job not found. It may have been removed or the ID is incorrect."));

        if (incrementViews && job.getStatus() == JobStatus.OPEN) {
            // Atomic increment in Redis; flush to DB every 50 views via @Scheduled
            Long views = redisTemplate.opsForValue()
                .increment(VIEWS_KEY + jobId);
            if (views != null && views % 50 == 0) {
                jobRepository.incrementViews(jobId);
                redisTemplate.expire(VIEWS_KEY + jobId, Duration.ofDays(7));
            }
        }

        long applicationCount = applicationRepository.countByJobId(jobId);
        return toResponse(job, applicationCount);
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobResponse> list(JobStatus status, String department,
                                           String location, String search,
                                           Pageable pageable) {
        Page<Job> page = jobRepository.findFiltered(status, department, location, search, pageable);
        return PagedResponse.of(page, j -> toResponse(j, applicationRepository.countByJobId(j.getId())));
    }

    @Transactional(readOnly = true)
    public PagedResponse<JobResponse> listByPoster(UUID userId, Pageable pageable) {
        Page<Job> page = jobRepository.findByPostedById(userId, pageable);
        return PagedResponse.of(page, j -> toResponse(j, applicationRepository.countByJobId(j.getId())));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"job-detail","job-list"}, allEntries = true)
    public JobResponse update(UUID jobId, UpdateJobRequest req, UUID requestingUserId, boolean isAdmin) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Job not found."));

        if (!isAdmin && !job.getPostedBy().getId().equals(requestingUserId)) {
            throw new ForbiddenException("You can only edit jobs that you posted.");
        }

        if (req.title()          != null) job.setTitle(req.title().strip());
        if (req.description()    != null) job.setDescription(req.description().strip());
        if (req.requirements()   != null) job.setRequirements(req.requirements().strip());
        if (req.department()     != null) job.setDepartment(req.department().strip());
        if (req.location()       != null) job.setLocation(req.location().strip());
        if (req.employmentType() != null) job.setEmploymentType(req.employmentType());
        if (req.salaryMin()      != null) job.setSalaryMin(req.salaryMin());
        if (req.salaryMax()      != null) job.setSalaryMax(req.salaryMax());
        if (req.status()         != null) job.setStatus(req.status());
        if (req.deadline()       != null) job.setDeadline(req.deadline());

        job = jobRepository.save(job);
        auditLogger.log(requestingUserId, "JOB_UPDATED", "Job", job.getId(), null);
        long applicationCount = applicationRepository.countByJobId(jobId);
        return toResponse(job, applicationCount);
    }

    // ── Delete (soft) ─────────────────────────────────────────────────────────

    @Transactional
    @org.springframework.cache.annotation.CacheEvict(value = {"job-detail","job-list","dashboard-stats"}, allEntries = true)
    public void archive(UUID jobId, UUID requestingUserId, boolean isAdmin) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Job not found."));

        if (!isAdmin && !job.getPostedBy().getId().equals(requestingUserId)) {
            throw new ForbiddenException("You can only archive jobs that you posted.");
        }

        job.setStatus(JobStatus.ARCHIVED);
        jobRepository.save(job);
        auditLogger.log(requestingUserId, "JOB_ARCHIVED", "Job", jobId, null);
        log.info("Job archived: {} by user {}", jobId, requestingUserId);
    }

    // ── Publish / unpublish ───────────────────────────────────────────────────

    @Transactional
    public JobResponse publish(UUID jobId, UUID requestingUserId, boolean isAdmin) {
        Job job = jobRepository.findById(jobId)
            .orElseThrow(() -> new NotFoundException("Job not found."));

        if (!isAdmin && !job.getPostedBy().getId().equals(requestingUserId)) {
            throw new ForbiddenException("You can only publish jobs that you posted.");
        }
        if (job.getStatus() == JobStatus.ARCHIVED) {
            throw new com.smarthire.exception.BadRequestException(
                "Archived jobs cannot be published. Create a new posting instead.");
        }

        job.setStatus(JobStatus.OPEN);
        job = jobRepository.save(job);
        auditLogger.log(requestingUserId, "JOB_PUBLISHED", "Job", jobId, null);
        return toResponse(job, applicationRepository.countByJobId(jobId));
    }

    // ── Mapper ────────────────────────────────────────────────────────────────

    public JobResponse toResponse(Job job, long applicationCount) {
        return new JobResponse(
            job.getId(),
            job.getTitle(),
            job.getDescription(),
            job.getRequirements(),
            job.getDepartment(),
            job.getLocation(),
            job.getEmploymentType(),
            job.getSalaryMin(),
            job.getSalaryMax(),
            job.getCurrency(),
            job.getStatus(),
            job.getDeadline(),
            job.getViewsCount() != null ? job.getViewsCount() : 0L,
            applicationCount,
            job.getPostedBy() != null ? job.getPostedBy().getId() : null,
            job.getPostedBy() != null
                ? job.getPostedBy().getFirstName() + " " + job.getPostedBy().getLastName()
                : null,
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }
}
