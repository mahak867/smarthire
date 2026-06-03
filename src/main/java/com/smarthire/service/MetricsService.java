// ── SmartHire · service/MetricsService.java ──
package com.smarthire.service;

import com.smarthire.domain.enums.ApplicationStatus;
import com.smarthire.domain.enums.JobStatus;
import com.smarthire.domain.repositories.ApplicationRepository;
import com.smarthire.domain.repositories.JobRepository;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom business metrics exposed to Prometheus/Grafana.
 *
 * Metrics:
 *   smarthire_open_jobs_total          — gauge: number of live job postings
 *   smarthire_applications_total       — counter: total applications ever submitted
 *   smarthire_ai_scoring_queue_depth   — gauge: applications pending AI scoring
 *   smarthire_ai_scoring_duration      — timer: scoring latency distribution
 *   smarthire_shortlisted_total        — gauge: candidates currently shortlisted
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry         meterRegistry;
    private final JobRepository         jobRepository;
    private final ApplicationRepository applicationRepository;

    private final AtomicInteger scoringQueueDepth = new AtomicInteger(0);

    @PostConstruct
    public void registerMetrics() {
        // Open jobs gauge — live count from DB
        Gauge.builder("smarthire.open.jobs", jobRepository,
                repo -> repo.countByStatus(JobStatus.OPEN))
            .description("Number of currently open job postings")
            .register(meterRegistry);

        // AI scoring queue depth
        Gauge.builder("smarthire.ai.scoring.queue.depth", scoringQueueDepth, AtomicInteger::get)
            .description("Number of applications currently awaiting AI scoring")
            .register(meterRegistry);

        // Shortlisted candidates gauge
        Gauge.builder("smarthire.shortlisted.candidates", applicationRepository,
                repo -> repo.countByStatus(ApplicationStatus.SHORTLISTED))
            .description("Candidates currently at SHORTLISTED or above stage")
            .register(meterRegistry);

        log.info("SmartHire custom metrics registered");
    }

    /** Call when scoring starts — increments queue depth counter. */
    public void scoringStarted() { scoringQueueDepth.incrementAndGet(); }

    /** Call when scoring ends (success or failure) — decrements queue depth. */
    public void scoringEnded()   { scoringQueueDepth.decrementAndGet(); }

    /** Record AI scoring duration for the Prometheus histogram. */
    public void recordScoringDuration(long milliseconds, String method) {
        Timer.builder("smarthire.ai.scoring.duration")
            .description("AI scoring pipeline duration")
            .tag("method", method)
            .register(meterRegistry)
            .record(milliseconds, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /** Increment the applications submitted counter. */
    public void applicationSubmitted(String jobDepartment) {
        Counter.builder("smarthire.applications.submitted")
            .description("Total applications submitted")
            .tag("department", jobDepartment != null ? jobDepartment : "unknown")
            .register(meterRegistry)
            .increment();
    }
}
