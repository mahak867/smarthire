// ── SmartHire · SmartHireApplication.java ──
package com.smarthire;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SmartHire — AI-Powered Recruitment Intelligence Platform.
 *
 * Entry point for the Spring Boot application.
 *
 * Annotations:
 *   @EnableScheduling — activates @Scheduled tasks (auto-archive, digest, token purge)
 *   @EnableAsync      — activates @Async methods (AI scoring, WebSocket push, email)
 *   @EnableCaching    — activates @Cacheable / @CacheEvict on services
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
public class SmartHireApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartHireApplication.class, args);
    }
}
