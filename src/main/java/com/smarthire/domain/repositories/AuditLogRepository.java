// ── SmartHire · src/main/java/com/smarthire/domain/repositories/AuditLogRepository.java ──
package com.smarthire.domain.repositories;

import com.smarthire.domain.entities.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {}
