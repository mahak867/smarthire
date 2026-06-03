// ── SmartHire · UserController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.api.v1.dto.response.PagedResponse;
import com.smarthire.api.v1.dto.response.UserResponse;
import com.smarthire.security.JwtTokenProvider;
import com.smarthire.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User management — ADMIN only")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService       userService;
    private final JwtTokenProvider  jwtTokenProvider;

    @Operation(summary = "List all users with filtering")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<UserResponse>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.list(role, search,
            PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())));
    }

    @Operation(summary = "Get a single user by ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @Operation(summary = "Change a user's role")
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> updateRole(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails principal) {
        UUID adminId = jwtTokenProvider.extractUserId(principal.getUsername());
        return ResponseEntity.ok(userService.updateRole(id, body.get("role"), adminId));
    }

    @Operation(summary = "Activate or deactivate a user account")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> setActive(
            @PathVariable UUID id,
            @RequestBody Map<String, Boolean> body,
            @AuthenticationPrincipal UserDetails principal) {
        UUID adminId = jwtTokenProvider.extractUserId(principal.getUsername());
        boolean active = Boolean.TRUE.equals(body.get("active"));
        return ResponseEntity.ok(userService.setActive(id, active, adminId));
    }

    @Operation(summary = "Unlock a locked account (clear failed-attempt counter)")
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unlock(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {
        UUID adminId = jwtTokenProvider.extractUserId(principal.getUsername());
        userService.unlockAccount(id, adminId);
        return ResponseEntity.noContent().build();
    }
}
