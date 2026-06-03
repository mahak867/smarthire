// ── SmartHire · ScheduleInterviewRequest.java ──
package com.smarthire.api.v1.dto.request;

import com.smarthire.domain.enums.InterviewType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleInterviewRequest(
    @NotNull(message = "Application ID is required") UUID applicationId,
    @NotNull(message = "Interviewer ID is required")  UUID interviewerId,
    @NotNull(message = "Scheduled time is required")
    @Future(message = "Interview must be scheduled in the future")
    OffsetDateTime scheduledAt,
    Integer durationMinutes,
    @NotNull(message = "Interview type is required") InterviewType type,
    String meetingLink,
    String notes
) {}
