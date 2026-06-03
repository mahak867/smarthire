// ── SmartHire · src/test/java/com/smarthire/api/ApplicationControllerTest.java ──
package com.smarthire.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthire.api.v1.dto.request.*;
import com.smarthire.api.v1.dto.response.*;
import com.smarthire.domain.enums.UserRole;
import com.smarthire.security.JwtTokenProvider;
import com.smarthire.service.ApplicationService;
import com.smarthire.service.RateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("ApplicationController — endpoint security and behaviour tests")
class ApplicationControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtTokenProvider tokenProvider;
    @MockBean  ApplicationService applicationService;
    @MockBean  RateLimitService rateLimitService;

    private String candidateToken;
    private String recruiterToken;
    private UUID candidateId;
    private UUID recruiterId;

    @BeforeEach
    void setUp() {
        candidateId  = UUID.randomUUID();
        recruiterId  = UUID.randomUUID();
        candidateToken  = tokenProvider.generateAccessToken(candidateId,  "candidate@test.com",  "CANDIDATE");
        recruiterToken  = tokenProvider.generateAccessToken(recruiterId,  "recruiter@test.com",  "RECRUITER");
        doNothing().when(rateLimitService).checkLoginLimit(any());
        doNothing().when(rateLimitService).checkRegisterLimit(any());
    }

    // ── Submit Application ──────────────────────────────────────────────────

    @Test
    @DisplayName("CANDIDATE can submit application → 201 Created")
    void submit_asCandidate_returns201() throws Exception {
        UUID jobId = UUID.randomUUID();
        SubmitApplicationRequest request = new SubmitApplicationRequest(
            jobId, "https://s3.example.com/resume.pdf", "I am excited about this role.");

        ApplicationResponse mockResponse = buildApplicationResponse(jobId);
        when(applicationService.submit(any(), eq(candidateId))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + candidateToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.jobId").value(jobId.toString()))
            .andExpect(jsonPath("$.scoringComplete").value(false));
    }

    @Test
    @DisplayName("RECRUITER cannot submit application → 403 Forbidden")
    void submit_asRecruiter_returns403() throws Exception {
        SubmitApplicationRequest request = new SubmitApplicationRequest(
            UUID.randomUUID(), "https://s3.example.com/resume.pdf", null);

        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + recruiterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());

        verifyNoInteractions(applicationService);
    }

    @Test
    @DisplayName("Unauthenticated request → 401 Unauthorized")
    void submit_noToken_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CANDIDATE cannot view another candidate's application → 403")
    void getById_otherCandidateApplication_returns403() throws Exception {
        UUID appId = UUID.randomUUID();
        when(applicationService.getById(eq(appId), eq(candidateId)))
            .thenThrow(new com.smarthire.exception.ForbiddenException(
                "You do not have permission to view this application."));

        mockMvc.perform(get("/api/v1/applications/{id}", appId)
                .header("Authorization", "Bearer " + candidateToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("RECRUITER can update application status → 200 OK")
    void updateStatus_asRecruiter_returns200() throws Exception {
        UUID appId = UUID.randomUUID();
        UpdateApplicationStatusRequest request = new UpdateApplicationStatusRequest("SHORTLISTED", "Good fit");
        ApplicationResponse mockResponse = buildApplicationResponse(UUID.randomUUID());

        when(applicationService.updateStatus(eq(appId), any(), eq(recruiterId))).thenReturn(mockResponse);

        mockMvc.perform(put("/api/v1/applications/{id}/status", appId)
                .header("Authorization", "Bearer " + recruiterToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Ranked endpoint returns applications sorted by AI score — RECRUITER only")
    void rankedByJob_asRecruiter_returnsOk() throws Exception {
        UUID jobId = UUID.randomUUID();
        List<ApplicationResponse> ranked = List.of(
            buildApplicationResponseWithScore(jobId, new BigDecimal("87.5")),
            buildApplicationResponseWithScore(jobId, new BigDecimal("72.0")),
            buildApplicationResponseWithScore(jobId, new BigDecimal("54.3"))
        );
        when(applicationService.getRankedByJob(jobId)).thenReturn(ranked);

        mockMvc.perform(get("/api/v1/applications/job/{jobId}/ranked", jobId)
                .header("Authorization", "Bearer " + recruiterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].aiScore").value(87.5));
    }

    @Test
    @DisplayName("CANDIDATE cannot access ranked endpoint → 403")
    void rankedByJob_asCandidate_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/applications/job/{jobId}/ranked", UUID.randomUUID())
                .header("Authorization", "Bearer " + candidateToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Validation error on missing jobId → 422 with field errors")
    void submit_missingJobId_returnsValidationError() throws Exception {
        String invalidBody = """
            { "resumeUrl": "https://example.com/resume.pdf" }
            """;
        mockMvc.perform(post("/api/v1/applications")
                .header("Authorization", "Bearer " + candidateToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.fieldErrors.jobId").exists());
    }

    @Test
    @DisplayName("Security headers present on every response")
    void everyResponse_hasSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/applications/my")
                .header("Authorization", "Bearer " + candidateToken))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().exists("X-Request-ID"));
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private ApplicationResponse buildApplicationResponse(UUID jobId) {
        return buildApplicationResponseWithScore(jobId, null);
    }

    private ApplicationResponse buildApplicationResponseWithScore(UUID jobId, BigDecimal score) {
        return new ApplicationResponse(
            UUID.randomUUID(), jobId, "Senior Java Developer",
            candidateId, "Jane Doe", "jane@test.com",
            "https://s3.example.com/resume.pdf", "Cover letter text",
            "APPLIED", score, score != null ? "Strong candidate" : null,
            score != null ? score.subtract(new BigDecimal("5")) : null,
            score != null ? Map.of("scoring_method", "tfidf_only") : null,
            score != null, null, OffsetDateTime.now(), OffsetDateTime.now()
        );
    }
}
