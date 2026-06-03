// ── SmartHire · ApplicationRepository.java ──
package com.smarthire.domain.repositories;

import com.smarthire.domain.entities.Application;
import com.smarthire.domain.enums.ApplicationStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    // ── Existence checks ──────────────────────────────────────────────────────

    boolean existsByJobIdAndCandidateId(UUID jobId, UUID candidateId);

    // ── Filtered list (recruiter view) ────────────────────────────────────────

    @EntityGraph(attributePaths = {"job", "candidate"})
    @Query("""
        SELECT a FROM Application a
        WHERE (:jobId   IS NULL OR a.job.id   = :jobId)
          AND (:status  IS NULL OR a.status   = :status)
          AND (:minScore IS NULL OR a.aiScore >= :minScore)
        """)
    Page<Application> findFiltered(
        @Param("jobId")    UUID jobId,
        @Param("status")   ApplicationStatus status,
        @Param("minScore") BigDecimal minScore,
        Pageable pageable);

    // ── Candidate's own applications ──────────────────────────────────────────

    @EntityGraph(attributePaths = {"job", "candidate"})
    @Query("SELECT a FROM Application a WHERE a.candidate.id = :candidateId ORDER BY a.appliedAt DESC")
    Page<Application> findByCandidateId(
        @Param("candidateId") UUID candidateId, Pageable pageable);

    // ── Ranked by AI score for a specific job ─────────────────────────────────

    /** Paged — used by export, ranked endpoint, and dashboard top-candidates. */
    @EntityGraph(attributePaths = {"job", "candidate"})
    @Query("SELECT a FROM Application a WHERE a.job.id = :jobId ORDER BY COALESCE(a.aiScore, 0) DESC NULLS LAST")
    Page<Application> findByJobIdOrderByAiScoreDesc(
        @Param("jobId") UUID jobId, Pageable pageable);

    /** Unpaged — used by pipeline CSV export and per-job stats. */
    @EntityGraph(attributePaths = {"job", "candidate"})
    @Query("SELECT a FROM Application a WHERE a.job.id = :jobId ORDER BY COALESCE(a.aiScore, 0) DESC NULLS LAST")
    List<Application> findByJobIdOrderByAiScoreDescUnpaged(
        @Param("jobId") UUID jobId);

    // ── Analytics ─────────────────────────────────────────────────────────────

    long countByJobId(UUID jobId);

    long countByAppliedAtAfter(OffsetDateTime since);

    /** Count per status — used by dashboard pipeline breakdown. */
    @Query("SELECT a.status, COUNT(a) FROM Application a GROUP BY a.status")
    List<Object[]> countGroupByStatus();

    /** Count per status restricted to OPEN jobs — used by metrics gauge. */
    @Query("""
        SELECT a.status, COUNT(a) FROM Application a
        WHERE a.job.status = com.smarthire.domain.enums.JobStatus.OPEN
        GROUP BY a.status
        """)
    List<Object[]> countByStatusForOpenJobs();

    /** Simple count by status — used by MetricsService gauges. */
    long countByStatus(ApplicationStatus status);

    /** Average days from submission to OFFERED over the last 90 days. */
    @Query(value =
        "SELECT AVG(EXTRACT(EPOCH FROM (updated_at - applied_at)) / 86400.0) " +
        "FROM applications WHERE status = 'OFFERED' AND applied_at > NOW() - INTERVAL '90 days'",
        nativeQuery = true)
    Double avgDaysToHire();

    // ── Dashboard top candidates ──────────────────────────────────────────────

    @Query("""
        SELECT a FROM Application a
        JOIN FETCH a.job j
        JOIN FETCH a.candidate c
        WHERE j.status = com.smarthire.domain.enums.JobStatus.OPEN
          AND a.aiScore IS NOT NULL
        ORDER BY a.aiScore DESC
        """)
    List<Application> findTopCandidates(Pageable pageable);

    // ── Scheduler ─────────────────────────────────────────────────────────────

    /** Candidates stuck in APPLIED before a cutoff date — for acknowledgement emails. */
    @EntityGraph(attributePaths = {"candidate", "job"})
    List<Application> findByStatusAndAppliedAtBefore(
        ApplicationStatus status, OffsetDateTime before);
}
