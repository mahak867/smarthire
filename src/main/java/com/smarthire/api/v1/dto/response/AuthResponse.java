// ── SmartHire · dto/response/AuthResponse.java ──
package com.smarthire.api.v1.dto.response;
public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {}
