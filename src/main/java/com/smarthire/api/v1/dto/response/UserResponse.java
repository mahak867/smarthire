// ── SmartHire · UserResponse.java ──
package com.smarthire.api.v1.dto.response;

import com.smarthire.domain.enums.UserRole;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    UserRole role,
    boolean active,
    boolean emailVerified,
    int failedLoginAttempts,
    boolean accountLocked,
    OffsetDateTime createdAt,
    OffsetDateTime lastLoginAt
) {}
