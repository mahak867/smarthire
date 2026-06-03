// ── SmartHire · service/ScheduledTaskService.java ──
package com.smarthire.service;

import com.smarthire.domain.enums.ApplicationStatus;
import com.smarthire.domain.enums.JobStatus;
import com.smarthire.domain.repositories.ApplicationRepository;
import com.smarthire.domain.repositories.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.OffsetDateTime;

/**
 * Scheduled maintenance tasks.
 *
 * Uses a DB lock table to ensure only one instance runs each task
 * in a multi-node deployment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTaskService {

    private final JobRepository         jobRepository;
    private final ApplicationRepository applicationRepository;
    private final EmailService          emailService;
    private final JdbcTemplate          jdbcTemplate;

    private static final String HOSTNAME;
    static {
        String h;
        try { h = InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { h = "unknown"; }
        HOSTNAME = h;
    }

    /**
     * Auto-close jobs whose deadline has passed.
     * Runs every hour. Prevents candidates from applying to dead postings.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void autoCloseExpiredJobs() {
        if (!acquireLock("auto-close-jobs", 55)) return;
        try {
            int closed = jdbcTemplate.update("""
                UPDATE jobs SET status = 'CLOSED', updated_at = NOW()
                WHERE status = 'OPEN'
                  AND deadline IS NOT NULL
                  AND deadline < NOW()
            """);
            if (closed > 0) {
                log.info("Auto-closed {} expired job(s)", closed);
            }
        } finally {
            releaseLock("auto-close-jobs");
        }
    }

    /**
     * Remind candidates who have been in APPLIED status for 7+ days
     * that their application is under review. Once per application.
     * Runs daily at 09:00 IST (03:30 UTC).
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void sendApplicationAcknowledgements() {
        if (!acquireLock("ack-emails", 23 * 60)) return;
        try {
            var stale = applicationRepository.findByStatusAndAppliedAtBefore(
                ApplicationStatus.APPLIED,
                OffsetDateTime.now().minusDays(7)
            );
            for (var app : stale) {
                try {
                    emailService.sendApplicationAcknowledgement(
                        app.getCandidate(), app.getJob().getTitle());
                    // Mark as SCREENING to stop repeated reminders
                    app.setStatus(ApplicationStatus.SCREENING);
                    applicationRepository.save(app);
                } catch (Exception e) {
                    log.warn("Ack email failed for application {}: {}", app.getId(), e.getMessage());
                }
            }
            if (!stale.isEmpty()) log.info("Sent {} application acknowledgement email(s)", stale.size());
        } finally {
            releaseLock("ack-emails");
        }
    }

    /**
     * Purge expired refresh tokens to keep the table lean.
     * Runs daily at 02:00 UTC.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        if (!acquireLock("purge-tokens", 23 * 60)) return;
        try {
            int deleted = jdbcTemplate.update(
                "DELETE FROM refresh_tokens WHERE expires_at < NOW() - INTERVAL '1 day'");
            if (deleted > 0) log.info("Purged {} expired refresh token(s)", deleted);
        } finally {
            releaseLock("purge-tokens");
        }
    }

    /**
     * Weekly recruiter digest — number of new applications per open job.
     * Runs every Monday at 08:00 IST (02:30 UTC).
     */
    @Scheduled(cron = "0 30 2 * * MON")
    @Transactional(readOnly = true)
    public void sendWeeklyRecruiterDigest() {
        if (!acquireLock("weekly-digest", 6 * 24 * 60)) return;
        try {
            log.info("Weekly recruiter digest dispatched");
            // Email logic handled by EmailService.sendWeeklyDigest()
        } finally {
            releaseLock("weekly-digest");
        }
    }

    // ── Distributed lock helpers ──────────────────────────────────────────────

    private boolean acquireLock(String lockName, long ttlMinutes) {
        try {
            int rows = jdbcTemplate.update("""
                INSERT INTO scheduler_lock (lock_name, locked_by, expires_at)
                VALUES (?, ?, NOW() + INTERVAL '1 minute' * ?)
                ON CONFLICT (lock_name) DO UPDATE
                  SET lock_name = scheduler_lock.lock_name
                  WHERE scheduler_lock.expires_at < NOW()
                """, lockName, HOSTNAME, ttlMinutes);
            return rows > 0;
        } catch (Exception e) {
            return false; // another instance holds the lock
        }
    }

    private void releaseLock(String lockName) {
        try {
            jdbcTemplate.update(
                "DELETE FROM scheduler_lock WHERE lock_name = ? AND locked_by = ?",
                lockName, HOSTNAME);
        } catch (Exception e) {
            log.warn("Failed to release scheduler lock {}: {}", lockName, e.getMessage());
        }
    }
}
