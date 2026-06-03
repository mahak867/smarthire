// ── SmartHire · CreateJobRequest.java ──
package com.smarthire.api.v1.dto.request;

import com.smarthire.domain.enums.EmploymentType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateJobRequest(
    @NotBlank(message = "Job title is required")
    @Size(min = 3, max = 200, message = "Title must be 3–200 characters")
    String title,

    @NotBlank(message = "Job description is required")
    @Size(min = 50, message = "Description must be at least 50 characters")
    String description,

    @NotBlank(message = "Job requirements are required")
    @Size(min = 20, message = "Requirements must be at least 20 characters")
    String requirements,

    @Size(max = 100) String department,
    @Size(max = 200) String location,

    @NotNull(message = "Employment type is required")
    EmploymentType employmentType,

    @DecimalMin(value = "0", message = "Minimum salary cannot be negative")
    BigDecimal salaryMin,

    @DecimalMin(value = "0", message = "Maximum salary cannot be negative")
    BigDecimal salaryMax,

    @Pattern(regexp = "INR|USD|GBP|EUR|AED", message = "Unsupported currency")
    String currency,

    OffsetDateTime deadline,

    /** If true, status is set to OPEN immediately. Otherwise saved as DRAFT. */
    Boolean publishNow
) {}
