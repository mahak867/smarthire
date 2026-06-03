// ── SmartHire · src/main/java/com/smarthire/service/AuthService.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.request.*;
import com.smarthire.api.v1.dto.response.*;
import com.smarthire.domain.entities.User;
import com.smarthire.domain.enums.UserRole;
import com.smarthire.domain.repositories.RefreshTokenRepository;
import com.smarthire.domain.repositories.UserRepository;
import com.smarthire.exception.*;
import com.smarthire.security.JwtTokenProvider;
import com.smarthire.util.AuditLogger;
import com.smarthire.service.AccountLockoutService;
import com.smarthire.domain.repositories.PasswordHistoryRepository;
import com.smarthire.domain.entities.PasswordHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    @Transactional
    public UserResponse register(RegisterRequest request, String ipAddress, String userAgent) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException(
                "An account with email '" + request.email() + "' already exists.");
        }

        User user = User.builder()
            .email(request.email().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.password()))
            .firstName(sanitise(request.firstName()))
            .lastName(sanitise(request.lastName()))
            .role(UserRole.CANDIDATE)
            .active(true)
            .emailVerified(false)
            .build();

        user = userRepository.save(user);
        auditLogger.log(user.getId(), "USER_REGISTER", "USER", user.getId(), ipAddress, userAgent, null);
        log.info("New user registered: {}", user.getEmail());
        return toUserResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> {
                auditLogger.log(null, "LOGIN_FAILED_NO_USER", "AUTH", null, ipAddress, userAgent,
                    java.util.Map.of("email", request.email()));
                return new UnauthorisedException("Incorrect email or password.");
            });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditLogger.log(user.getId(), "LOGIN_FAILED_BAD_PASSWORD", "AUTH", null, ipAddress, userAgent, null);
            boolean nowLocked = lockoutService.recordFailedAttempt(user);
            throw new UnauthorisedException(nowLocked
                ? lockoutService.lockedMessage(user)
                : "Incorrect email or password.");
        }

        if (user.isAccountLocked()) {
            throw new UnauthorisedException(lockoutService.lockedMessage(user));
        }

        if (!user.isActive()) {
            throw new UnauthorisedException("Your account has been deactivated. Please contact support.");
        }

        user.setLastLoginAt(OffsetDateTime.now());
        userRepository.save(user);

        String accessToken  = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId());
        persistRefreshToken(user, refreshToken, ipAddress, userAgent);

        auditLogger.log(user.getId(), "LOGIN_SUCCESS", "AUTH", null, ipAddress, userAgent, null);
        return new AuthResponse(accessToken, refreshToken,
            tokenProvider.getAccessTokenExpiryMs() / 1000, toUserResponse(user));
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        if (!tokenProvider.validateToken(rawRefreshToken)) {
            throw new UnauthorisedException("Your session has expired. Please sign in again.");
        }

        String tokenHash = sha256(rawRefreshToken);
        var stored = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new UnauthorisedException("Invalid refresh token. Please sign in again."));

        if (stored.getRevokedAt() != null) {
            // Token reuse detected — revoke all tokens for this user
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), OffsetDateTime.now());
            auditLogger.log(stored.getUser().getId(), "REFRESH_TOKEN_REUSE_DETECTED", "AUTH",
                null, ipAddress, userAgent, null);
            throw new UnauthorisedException("Session invalidated due to suspicious activity. Please sign in again.");
        }

        if (stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new UnauthorisedException("Your session has expired. Please sign in again.");
        }

        // Revoke old token
        stored.setRevokedAt(OffsetDateTime.now());
        refreshTokenRepository.save(stored);

        User user = stored.getUser();
        String newAccess  = tokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String newRefresh = tokenProvider.generateRefreshToken(user.getId());
        persistRefreshToken(user, newRefresh, ipAddress, userAgent);

        return new AuthResponse(newAccess, newRefresh,
            tokenProvider.getAccessTokenExpiryMs() / 1000, toUserResponse(user));
    }

    @Transactional
    public void logout(UUID userId, String rawRefreshToken) {
        String tokenHash = sha256(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(rt -> {
            rt.setRevokedAt(OffsetDateTime.now());
            refreshTokenRepository.save(rt);
        });
        auditLogger.log(userId, "LOGOUT", "AUTH", null, null, null, null);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            auditLogger.log(userId, "PASSWORD_CHANGE_FAILED", "AUTH", null, ipAddress, userAgent, null);
            throw new UnauthorisedException("Current password is incorrect.");
        }

        // Enforce password history — reject if matches any of last 5 passwords
        var history = passwordHistoryRepository.findByUserIdOrderByCreatedAtDesc(
            userId, org.springframework.data.domain.PageRequest.of(0, 5));
        for (var h : history) {
            if (passwordEncoder.matches(request.newPassword(), h.getPasswordHash())) {
                throw new com.smarthire.exception.BadRequestException(
                    "This password has been used recently. Please choose a different password.");
            }
        }

        String newHash = passwordEncoder.encode(request.newPassword());
        // Save current password to history before overwriting
        passwordHistoryRepository.save(PasswordHistory.builder()
            .userId(userId).passwordHash(user.getPasswordHash()).build());
        user.setPasswordHash(newHash);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, OffsetDateTime.now());
        auditLogger.log(userId, "PASSWORD_CHANGED", "AUTH", null, ipAddress, userAgent, null);
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(UUID userId) {
        return userRepository.findById(userId)
            .map(this::toUserResponse)
            .orElseThrow(() -> new NotFoundException("User not found."));
    }

    // ── Helpers ──

    private void persistRefreshToken(User user, String rawToken, String ipAddress, String userAgent) {
        var rt = com.smarthire.domain.entities.RefreshToken.builder()
            .user(user)
            .tokenHash(sha256(rawToken))
            .expiresAt(OffsetDateTime.now().plusSeconds(
                tokenProvider.getRefreshTokenExpiryMs() / 1000))
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .build();
        refreshTokenRepository.save(rt);
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(),
            user.getFirstName(), user.getLastName(),
            user.getRole().name(), user.isEmailVerified(), user.getCreatedAt());
    }

    private String sanitise(String input) {
        if (input == null) return null;
        return input.trim()
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
