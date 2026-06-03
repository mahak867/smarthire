// ── SmartHire · JobResponse.java ──
package com.smarthire.api.v1.dto.response;

import com.smarthire.domain.enums.EmploymentType;
import com.smarthire.domain.enums.JobStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
    UUID id,
    String title,
    String description,
    String requirements,
    String department,
    String location,
    EmploymentType employmentType,
    BigDecimal salaryMin,
    BigDecimal salaryMax,
    String currency,
    JobStatus status,
    OffsetDateTime deadline,
    long viewsCount,
    long applicationCount,
    UUID postedById,
    String postedByName,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
