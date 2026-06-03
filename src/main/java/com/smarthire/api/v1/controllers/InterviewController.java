// ── SmartHire · InterviewController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.api.v1.dto.request.ScheduleInterviewRequest;
import com.smarthire.api.v1.dto.response.InterviewResponse;
import com.smarthire.security.JwtTokenProvider;
import com.smarthire.service.InterviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
@Tag(name = "Interviews", description = "Interview scheduling and feedback")
@SecurityRequirement(name = "bearerAuth")
public class InterviewController {

    private final InterviewService  interviewService;
    private final JwtTokenProvider  jwtTokenProvider;

    @Operation(summary = "Schedule an interview for an application")
    @PostMapping
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<InterviewResponse> schedule(
            @Valid @RequestBody ScheduleInterviewRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(interviewService.schedule(req, userId));
    }

    @Operation(summary = "Get all interviews for an application")
    @GetMapping("/application/{applicationId}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<List<InterviewResponse>> byApplication(@PathVariable UUID applicationId) {
        return ResponseEntity.ok(interviewService.findByApplication(applicationId));
    }

    @Operation(summary = "Get upcoming interviews assigned to the current interviewer")
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<List<InterviewResponse>> myInterviews(
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        return ResponseEntity.ok(interviewService.findByInterviewer(userId));
    }

    @Operation(summary = "Submit feedback and rating after an interview")
    @PostMapping("/{id}/feedback")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<InterviewResponse> submitFeedback(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId   = jwtTokenProvider.extractUserId(principal.getUsername());
        String feedback = (String) body.get("feedback");
        Integer rating  = body.get("rating") instanceof Number n ? n.intValue() : null;
        return ResponseEntity.ok(interviewService.submitFeedback(id, feedback, rating, userId));
    }

    @Operation(summary = "Cancel a scheduled interview")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<InterviewResponse> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId  = jwtTokenProvider.extractUserId(principal.getUsername());
        boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(interviewService.cancel(id, userId, isAdmin));
    }
}
