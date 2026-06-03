// ── SmartHire · InterviewResponse.java ──
package com.smarthire.api.v1.dto.response;

import com.smarthire.domain.enums.InterviewStatus;
import com.smarthire.domain.enums.InterviewType;
import java.time.OffsetDateTime;
import java.util.UUID;

public record InterviewResponse(
    UUID id,
    UUID applicationId,
    String candidateName,
    String jobTitle,
    OffsetDateTime scheduledAt,
    int durationMinutes,
    InterviewType type,
    InterviewStatus status,
    UUID interviewerId,
    String interviewerName,
    String meetingLink,
    String notes,
    String feedback,
    Integer rating,
    OffsetDateTime createdAt
) {}
