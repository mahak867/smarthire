// ── SmartHire · src/main/java/com/smarthire/domain/repositories/JobRepository.java ──
package com.smarthire.domain.repositories;

import com.smarthire.domain.entities.Job;
import com.smarthire.domain.enums.JobStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public interface JobRepository extends JpaRepository<Job, UUID> {

    @Query("""
        SELECT j FROM Job j
        LEFT JOIN FETCH j.postedBy
        WHERE (:status IS NULL OR j.status = :status)
          AND (:department IS NULL OR j.department = :department)
          AND (:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%',:location,'%')))
          AND (:search IS NULL OR (:search IS NULL OR j.searchVector @@ plainto_tsquery('english', :search))
               )
        """)
    Page<Job> findFiltered(
        @Param("status") JobStatus status,
        @Param("department") String department,
        @Param("location") String location,
        @Param("search") String search,
        Pageable pageable);

    @Query("SELECT j FROM Job j LEFT JOIN FETCH j.postedBy WHERE j.postedBy.id = :userId")
    Page<Job> findByPostedById(@Param("userId") UUID userId, Pageable pageable);

    long countByStatus(JobStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Job j SET j.viewsCount = j.viewsCount + 1 WHERE j.id = :id")
    void incrementViews(@Param("id") UUID id);
}
