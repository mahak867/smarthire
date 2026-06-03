// ── SmartHire · src/main/java/com/smarthire/api/v1/controllers/AuthController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.api.v1.dto.request.*;
import com.smarthire.api.v1.dto.response.*;
import com.smarthire.service.AuthService;
import com.smarthire.service.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT auth — register, login, refresh, logout")
public class AuthController {

    private final AuthService authService;
    private final RateLimitService rateLimitService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        rateLimitService.checkRegisterLimit(getClientIp(httpRequest));
        UserResponse user = authService.register(request, getClientIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        rateLimitService.checkLoginLimit(getClientIp(httpRequest));
        AuthResponse auth = authService.login(request, getClientIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token using refresh token")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse auth = authService.refresh(request.refreshToken(),
            getClientIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke refresh token and log out", security = @SecurityRequirement(name = "bearer"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        authService.logout(UUID.fromString(principal.getUsername()), request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password (requires current password)", security = @SecurityRequirement(name = "bearer"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetails principal,
            HttpServletRequest httpRequest) {

        authService.changePassword(UUID.fromString(principal.getUsername()),
            request, getClientIp(httpRequest), getUserAgent(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = "bearer"))
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserDetails principal) {
        return ResponseEntity.ok(authService.getMe(UUID.fromString(principal.getUsername())));
    }

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }

    private String getUserAgent(HttpServletRequest req) {
        return req.getHeader("User-Agent");
    }
}
