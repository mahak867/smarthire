// ── SmartHire · UpdateJobRequest.java ──
package com.smarthire.api.v1.dto.request;

import com.smarthire.domain.enums.EmploymentType;
import com.smarthire.domain.enums.JobStatus;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateJobRequest(
    @Size(min = 3, max = 200, message = "Title must be 3–200 characters") String title,
    @Size(min = 50, message = "Description must be at least 50 characters")  String description,
    @Size(min = 20, message = "Requirements must be at least 20 characters")  String requirements,
    String department,
    String location,
    EmploymentType employmentType,
    @Positive(message = "Minimum salary must be positive") BigDecimal salaryMin,
    @Positive(message = "Maximum salary must be positive") BigDecimal salaryMax,
    JobStatus status,
    OffsetDateTime deadline
) {}
