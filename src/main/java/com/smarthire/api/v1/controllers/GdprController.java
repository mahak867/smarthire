// ── SmartHire · GdprController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.service.GdprService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gdpr")
@RequiredArgsConstructor
@Tag(name = "GDPR", description = "Data subject rights — erasure and portability")
@SecurityRequirement(name = "bearerAuth")
public class GdprController {

    private final GdprService      gdprService;

    @Operation(
        summary = "Request erasure of your personal data (Right to be Forgotten)",
        description = "Anonymises all PII associated with your account. " +
                      "This action is irreversible. An audit log entry is retained as required by law.")
    @DeleteMapping("/me")
    public ResponseEntity<Map<String, String>> eraseMyData(
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = java.util.UUID.fromString(principal.getUsername());
        String reason = body != null ? body.getOrDefault("reason", "User-initiated erasure") : "User-initiated erasure";
        gdprService.eraseUser(userId, userId, reason);
        return ResponseEntity.ok(Map.of(
            "message", "Your personal data has been anonymised. " +
                       "You will be signed out of all sessions."
        ));
    }

    @Operation(
        summary = "Export a copy of your personal data (Right to Portability)",
        description = "Returns all personal data held about you in a machine-readable format.")
    @GetMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportMyData(
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = java.util.UUID.fromString(principal.getUsername());
        return ResponseEntity.ok(gdprService.exportPortabilityData(userId));
    }

    @Operation(
        summary = "Admin: erase a specific user's data",
        description = "ADMIN only. Used for support requests and regulatory compliance.")
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> eraseUser(
            @PathVariable UUID id,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal) {

        UUID adminId = java.util.UUID.fromString(principal.getUsername());
        String reason = body != null ? body.getOrDefault("reason", "Admin-initiated erasure") : "Admin-initiated erasure";
        gdprService.eraseUser(id, adminId, reason);
        return ResponseEntity.ok(Map.of("message", "User data anonymised successfully."));
    }
}
