// ── SmartHire · src/main/java/com/smarthire/util/AuditLogger.java ──
package com.smarthire.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.domain.entities.AuditLog;
import com.smarthire.domain.repositories.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Async("auditExecutor")
    public void log(UUID userId, String action, String entityType, UUID entityId,
                    String ipAddress, String userAgent, Map<String, Object> payload) {
        try {
            String payloadJson = payload != null ? objectMapper.writeValueAsString(payload) : null;
            AuditLog entry = AuditLog.builder()
                .userId(userId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .ipAddress(ipAddress)
                .userAgent(userAgent != null && userAgent.length() > 500
                    ? userAgent.substring(0, 500) : userAgent)
                .payloadJson(payloadJson)
                .build();
            auditLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }

    /** Convenience overload — logs without IP/userAgent (for internal service calls). */
    @Async("auditExecutor")
    public void log(UUID userId, String action, String entityType, UUID entityId, String note) {
        Map<String, Object> payload = note != null ? Map.of("note", note) : null;
        log(userId, action, entityType, entityId, null, null, payload);
    }


}