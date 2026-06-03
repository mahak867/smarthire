// ── SmartHire · service/NotificationService.java ──
package com.smarthire.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

/**
 * Pushes real-time events to connected browser clients over WebSocket (STOMP).
 *
 * Topics:
 *   /topic/applications/{appId}/score  — AI scoring complete, payload = score + recommendation
 *   /user/{userId}/queue/notifications  — personal alerts (status change, interview scheduled)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Push AI score result to the recruiter channel for this application.
     * Called by AiScoringService after scoring completes (async context).
     */
    @Async
    public void pushScoringComplete(UUID applicationId, double aiScore,
                                    double skillMatchPct, String recommendation,
                                    String summary, String scoringMethod) {
        Map<String, Object> payload = Map.of(
            "type",           "SCORING_COMPLETE",
            "applicationId",  applicationId.toString(),
            "aiScore",        Math.round(aiScore * 10.0) / 10.0,
            "skillMatchPct",  Math.round(skillMatchPct * 10.0) / 10.0,
            "recommendation", recommendation != null ? recommendation : "MAYBE",
            "summary",        summary != null ? summary : "",
            "scoringMethod",  scoringMethod != null ? scoringMethod : "tfidf_only"
        );

        String dest = "/topic/applications/" + applicationId + "/score";
        try {
            messagingTemplate.convertAndSend(dest, payload);
            log.debug("Pushed scoring complete to {}", dest);
        } catch (Exception e) {
            log.warn("WebSocket push failed for {}: {}", dest, e.getMessage());
        }
    }

    /**
     * Push a personal notification to a specific user (status change, interview, etc.).
     */
    @Async
    public void pushUserNotification(UUID userId, String type, String title, String message) {
        Map<String, Object> payload = Map.of(
            "type",    type,
            "title",   title,
            "message", message,
            "ts",      System.currentTimeMillis()
        );
        try {
            messagingTemplate.convertAndSendToUser(
                userId.toString(), "/queue/notifications", payload);
        } catch (Exception e) {
            log.warn("User notification push failed for {}: {}", userId, e.getMessage());
        }
    }

    /** Push a global platform event (e.g. system maintenance warning). */
    @Async
    public void pushBroadcast(String type, String message) {
        try {
            messagingTemplate.convertAndSend("/topic/platform",
                Map.of("type", type, "message", message, "ts", System.currentTimeMillis()));
        } catch (Exception e) {
            log.warn("Broadcast push failed: {}", e.getMessage());
        }
    }
}
