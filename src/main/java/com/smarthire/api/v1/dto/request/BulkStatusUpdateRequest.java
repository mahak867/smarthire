// ── SmartHire · BulkStatusUpdateRequest.java ──
package com.smarthire.api.v1.dto.request;

import com.smarthire.domain.enums.ApplicationStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkStatusUpdateRequest(
    @NotEmpty(message = "At least one application ID is required")
    @Size(max = 200, message = "Maximum 200 applications per bulk operation")
    List<UUID> applicationIds,

    @NotNull(message = "Target status is required")
    ApplicationStatus status,

    String note
) {}
