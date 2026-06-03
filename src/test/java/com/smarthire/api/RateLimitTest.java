// ── SmartHire · src/test/java/com/smarthire/api/RateLimitTest.java ──
package com.smarthire.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.api.v1.dto.request.LoginRequest;
import com.smarthire.exception.RateLimitException;
import com.smarthire.service.AuthService;
import com.smarthire.service.RateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("Rate Limiting — brute force and abuse protection")
class RateLimitTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  AuthService authService;
    @MockBean  RateLimitService rateLimitService;

    @Test
    @DisplayName("6th login attempt in 15 minutes → 429 with Retry-After header")
    void login_sixthAttempt_returns429WithRetryAfter() throws Exception {
        long retryAfterSeconds = 847L; // remaining window time

        doThrow(new RateLimitException(
            "Too many login attempts. Please wait 14 minutes before trying again.",
            retryAfterSeconds))
            .when(rateLimitService).checkLoginLimit(any());

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("hacker@bad.com", "password"))))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("Retry-After", String.valueOf(retryAfterSeconds)))
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
            .andExpect(jsonPath("$.error").value(
                "Too many login attempts. Please wait 14 minutes before trying again."))
            .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("4th register attempt in 1 hour → 429 with Retry-After header")
    void register_fourthAttempt_returns429() throws Exception {
        doThrow(new RateLimitException(
            "Too many registration attempts. Please wait before trying again.", 3200L))
            .when(rateLimitService).checkRegisterLimit(any());

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"test@test.com","password":"Password1","firstName":"Test","lastName":"User"}
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"));
    }

    @Test
    @DisplayName("Normal login attempt does not get rate limited")
    void login_normalAttempt_notRateLimited() throws Exception {
        doNothing().when(rateLimitService).checkLoginLimit(any());
        when(authService.login(any(), anyString(), anyString()))
            .thenThrow(new com.smarthire.exception.UnauthorisedException("Incorrect email or password."));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new LoginRequest("test@test.com", "WrongPassword1"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORISED"));
    }
}
