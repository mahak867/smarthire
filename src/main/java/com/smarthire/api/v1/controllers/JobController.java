// ── SmartHire · src/main/java/com/smarthire/api/v1/controllers/JobController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.api.v1.dto.request.CreateJobRequest;
import com.smarthire.api.v1.dto.request.UpdateJobRequest;
import com.smarthire.api.v1.dto.response.JobResponse;
import com.smarthire.api.v1.dto.response.PagedResponse;
import com.smarthire.domain.enums.JobStatus;
import com.smarthire.domain.enums.UserRole;
import com.smarthire.security.JwtTokenProvider;
import com.smarthire.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Job posting management")
@SecurityRequirement(name = "bearerAuth")
public class JobController {

    private final JobService         jobService;
    private final JwtTokenProvider   jwtTokenProvider;

    // ── POST /api/v1/jobs ─────────────────────────────────────────────────────
    @Operation(summary = "Create a job posting", description = "RECRUITER or ADMIN only. Set publishNow=true to go live immediately.")
    @PostMapping
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<JobResponse> create(
            @Valid @RequestBody CreateJobRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        JobResponse response = jobService.create(req, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/v1/jobs ──────────────────────────────────────────────────────
    @Operation(summary = "List jobs with filtering and pagination")
    @GetMapping
    public ResponseEntity<PagedResponse<JobResponse>> list(
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc")
            ? Sort.by(sortBy).ascending()
            : Sort.by(sortBy).descending();
        PagedResponse<JobResponse> resp = jobService.list(
            status, department, location, search,
            PageRequest.of(page, Math.min(size, 100), sort));
        return ResponseEntity.ok(resp);
    }

    // ── GET /api/v1/jobs/my ───────────────────────────────────────────────────
    @Operation(summary = "List jobs posted by the current recruiter")
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<PagedResponse<JobResponse>> myJobs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        return ResponseEntity.ok(
            jobService.listByPoster(userId, PageRequest.of(page, Math.min(size, 100))));
    }

    // ── GET /api/v1/jobs/{id} ─────────────────────────────────────────────────
    @Operation(summary = "Get job details — increments view counter for public users")
    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getById(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean trackView) {

        return ResponseEntity.ok(jobService.findById(id, trackView));
    }

    // ── PUT /api/v1/jobs/{id} ─────────────────────────────────────────────────
    @Operation(summary = "Update a job posting (owner or admin only)")
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<JobResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJobRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(jobService.update(id, req, userId, isAdmin));
    }

    // ── POST /api/v1/jobs/{id}/publish ────────────────────────────────────────
    @Operation(summary = "Publish a draft job (changes status to OPEN)")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<JobResponse> publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return ResponseEntity.ok(jobService.publish(id, userId, isAdmin));
    }

    // ── DELETE /api/v1/jobs/{id} ──────────────────────────────────────────────
    @Operation(summary = "Archive a job posting — soft delete, preserves application history")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<Void> archive(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        boolean isAdmin = principal.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        jobService.archive(id, userId, isAdmin);
        return ResponseEntity.noContent().build();
    }
}
