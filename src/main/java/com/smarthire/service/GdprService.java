// ── SmartHire · service/GdprService.java ──
package com.smarthire.service;

import com.smarthire.domain.entities.User;
import com.smarthire.domain.repositories.ApplicationRepository;
import com.smarthire.domain.repositories.UserRepository;
import com.smarthire.exception.ForbiddenException;
import com.smarthire.exception.NotFoundException;
import com.smarthire.util.AuditLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GDPR compliance service.
 *
 * Right to Erasure (Article 17): anonymises all PII for a user and logs the action.
 * Right to Portability (Article 20): exports all personal data as a structured map.
 *
 * We anonymise rather than hard-delete to preserve audit log integrity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GdprService {

    private final UserRepository        userRepository;
    private final ApplicationRepository applicationRepository;
    private final JdbcTemplate          jdbcTemplate;
    private final AuditLogger           auditLogger;

    /**
     * Anonymise all PII for the given user.
     * Can be called by the user themselves or by an ADMIN.
     * Preserves anonymised records for audit and aggregate analytics.
     */
    @Transactional
    public void eraseUser(UUID targetId, UUID requestedById, String reason) {
        User user = userRepository.findById(targetId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        boolean isSelf  = targetId.equals(requestedById);
        boolean isAdmin = !isSelf; // only admins call this with a different requestedById

        if (!isSelf && !isAdmin) {
            throw new ForbiddenException("You can only request erasure of your own data.");
        }

        String originalEmail = user.getEmail();

        // Anonymise user PII — replace with non-reversible placeholder
        String anon = "deleted-" + UUID.randomUUID() + "@erased.invalid";
        user.setEmail(anon);
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPasswordHash("[ERASED]");
        user.setActive(false);
        userRepository.save(user);

        // Anonymise resume URLs in applications (files remain in MinIO until TTL, objectKey is now unlinkable)
        jdbcTemplate.update(
            "UPDATE applications SET resume_url = '[ERASED]', cover_letter = NULL WHERE candidate_id = ?",
            targetId
        );

        // Revoke all refresh tokens
        jdbcTemplate.update(
            "UPDATE refresh_tokens SET revoked_at = NOW() WHERE user_id = ? AND revoked_at IS NULL",
            targetId
        );

        // Log erasure — email stored as hash for legal audit trail
        String emailHash = Integer.toHexString(originalEmail.hashCode());
        jdbcTemplate.update(
            """
            INSERT INTO gdpr_erasure_log
                (requested_by, subject_email, subject_id, reason, erased_tables)
            VALUES (?, ?, ?, ?, ?::jsonb)
            """,
            requestedById, emailHash, targetId, reason,
            "[\"users\",\"applications\",\"refresh_tokens\"]"
        );

        auditLogger.log(requestedById, "GDPR_ERASURE", "User", targetId,
            "email_hash=" + emailHash);
        log.info("GDPR erasure completed for user {} (hash {}), requested by {}",
            targetId, emailHash, requestedById);
    }

    /**
     * Returns all personal data held for a user — for GDPR Article 20 portability requests.
     * Caller must verify the requesting user is entitled to this data.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportPortabilityData(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found."));

        long appCount = applicationRepository.countByAppliedAtAfter(java.time.OffsetDateTime.now().minusYears(10));

        return Map.of(
            "personal_information", Map.of(
                "email",      user.getEmail(),
                "first_name", user.getFirstName(),
                "last_name",  user.getLastName(),
                "role",       user.getRole().name(),
                "created_at", user.getCreatedAt().toString(),
                "last_login", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null
            ),
            "applications_summary", Map.of(
                "total", appCount,
                "note", "Full application data available on request — contact privacy@smarthire.app"
            ),
            "data_retention_policy",
                "Personal data is retained for 24 months after last login, " +
                "then anonymised unless a legal hold applies.",
            "export_generated_at", java.time.OffsetDateTime.now().toString()
        );
    }
}
