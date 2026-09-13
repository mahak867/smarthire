package com.smarthire.util;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Appends a timestamped line for every significant action (login, job posted,
 * application submitted, status changes, etc.) to smarthire_data/audit.log.
 * Mirrors the AuditLogger/AuditLog feature of the original Spring Boot app,
 * implemented here with plain java.nio.file.Files.
 */
public class AuditLogger {

    private final Path logFile;

    public AuditLogger(String dataDir) {
        this.logFile = Paths.get(dataDir, "audit.log");
        try {
            Files.createDirectories(logFile.getParent());
            if (!Files.exists(logFile)) Files.createFile(logFile);
        } catch (IOException e) {
            throw new RuntimeException("Could not initialize audit log", e);
        }
    }

    public void log(String actorEmail, String action) {
        String line = LocalDateTime.now() + " | " + actorEmail + " | " + action;
        try {
            Files.write(logFile, (line + System.lineSeparator()).getBytes(),
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Auditing failures should never crash the app - just note it on screen.
            System.out.println("[!] Warning: could not write to audit log: " + e.getMessage());
        }
    }

    public List<String> readAll() {
        try {
            return Files.readAllLines(logFile);
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }
}
