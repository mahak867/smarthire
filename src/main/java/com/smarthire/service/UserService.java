// ── SmartHire · service/UserService.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.response.PagedResponse;
import com.smarthire.api.v1.dto.response.UserResponse;
import com.smarthire.domain.entities.User;
import com.smarthire.domain.enums.UserRole;
import com.smarthire.domain.repositories.UserRepository;
import com.smarthire.exception.BadRequestException;
import com.smarthire.exception.ForbiddenException;
import com.smarthire.exception.NotFoundException;
import com.smarthire.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository  userRepository;
    private final AuditLogger     auditLogger;

    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> list(String role, String search, Pageable pageable) {
        return PagedResponse.of(
            userRepository.findFiltered(role, search, pageable),
            this::toResponse
        );
    }

    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        return toResponse(userRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("User not found.")));
    }

    @Transactional
    public UserResponse updateRole(UUID targetId, String newRole, UUID adminId) {
        UserRole role;
        try {
            role = UserRole.valueOf(newRole.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role. Must be one of: ADMIN, RECRUITER, CANDIDATE.");
        }

        if (targetId.equals(adminId)) {
            throw new ForbiddenException("You cannot change your own role.");
        }

        User user = userRepository.findById(targetId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        UserRole previousRole = user.getRole();
        user.setRole(role);
        userRepository.save(user);

        auditLogger.log(adminId, "USER_ROLE_CHANGED", "User", targetId,
            String.format("role changed from %s to %s", previousRole, role));
        log.info("User {} role changed {} → {} by admin {}", targetId, previousRole, role, adminId);
        return toResponse(user);
    }

    @Transactional
    public UserResponse setActive(UUID targetId, boolean active, UUID adminId) {
        if (targetId.equals(adminId)) {
            throw new ForbiddenException("You cannot deactivate your own account.");
        }

        User user = userRepository.findById(targetId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        user.setActive(active);
        // Clear lockout when admin manually reactivates
        if (active) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
        }
        userRepository.save(user);

        String action = active ? "USER_ACTIVATED" : "USER_DEACTIVATED";
        auditLogger.log(adminId, action, "User", targetId, null);
        log.info("User {} {} by admin {}", targetId, action, adminId);
        return toResponse(user);
    }

    @Transactional
    public void unlockAccount(UUID targetId, UUID adminId) {
        User user = userRepository.findById(targetId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastFailedAt(null);
        userRepository.save(user);
        auditLogger.log(adminId, "ACCOUNT_UNLOCKED", "User", targetId, null);
    }

    public UserResponse toResponse(User u) {
        return new UserResponse(
            u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
            u.getRole(), u.isActive(), u.isEmailVerified(),
            u.getFailedLoginAttempts(), u.isAccountLocked(),
            u.getCreatedAt(), u.getLastLoginAt()
        );
    }
}
