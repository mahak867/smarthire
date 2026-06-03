// ── SmartHire · InterviewRepository.java ──
package com.smarthire.domain.repositories;

import com.smarthire.domain.entities.Interview;
import com.smarthire.domain.enums.InterviewStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.job", "interviewer"})
    List<Interview> findByApplicationIdOrderByScheduledAtDesc(UUID applicationId);

    @EntityGraph(attributePaths = {"application", "application.candidate", "application.job", "interviewer"})
    List<Interview> findByInterviewerIdAndStatus(UUID interviewerId, InterviewStatus status);
}
