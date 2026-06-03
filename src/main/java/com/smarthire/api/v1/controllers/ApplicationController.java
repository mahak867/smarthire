// ── SmartHire · src/main/java/com/smarthire/api/v1/controllers/ApplicationController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.api.v1.dto.request.*;
import com.smarthire.api.v1.dto.response.*;
import com.smarthire.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
@Tag(name = "Applications", description = "Submit, view, score, and manage job applications")
@SecurityRequirement(name = "bearer")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping
    @Operation(summary = "Submit an application (triggers async AI scoring)")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<ApplicationResponse> submit(
            @Valid @RequestBody SubmitApplicationRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        ApplicationResponse app = applicationService.submit(request, UUID.fromString(principal.getUsername()));
        return ResponseEntity.status(HttpStatus.CREATED).body(app);
    }

    @GetMapping
    @Operation(summary = "List all applications (paginated, filterable)")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<PagedResponse<ApplicationResponse>> list(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Double minScore,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "appliedAt,desc") String sort) {

        PagedResponse<ApplicationResponse> result = applicationService.list(jobId, status, minScore,
            PageRequest.of(page, Math.min(size, 100), parseSort(sort)));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get application detail with AI score breakdown")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApplicationResponse> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        ApplicationResponse app = applicationService.getById(id, UUID.fromString(principal.getUsername()));
        return ResponseEntity.ok(app);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update application pipeline status")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<ApplicationResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApplicationStatusRequest request,
            @AuthenticationPrincipal UserDetails principal) {

        ApplicationResponse app = applicationService.updateStatus(id, request,
            UUID.fromString(principal.getUsername()));
        return ResponseEntity.ok(app);
    }

    @GetMapping("/my")
    @Operation(summary = "Get current candidate's own applications")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<PagedResponse<ApplicationResponse>> myApplications(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails principal) {

        PagedResponse<ApplicationResponse> result = applicationService.getByCandidate(
            UUID.fromString(principal.getUsername()),
            PageRequest.of(page, Math.min(size, 100)));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/job/{jobId}/ranked")
    @Operation(summary = "Get applications for a job ranked by AI score descending")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<List<ApplicationResponse>> rankedByJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(applicationService.getRankedByJob(jobId));
    }

    private Sort parseSort(String sortParam) {
        try {
            String[] parts = sortParam.split(",");
            Sort.Direction dir = parts.length > 1 && parts[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC : Sort.Direction.DESC;
            return Sort.by(dir, parts[0]);
        } catch (Exception e) {
            return Sort.by(Sort.Direction.DESC, "appliedAt");
        }
    }

    @Operation(summary = "Bulk update status for multiple applications",
               description = "Move up to 200 applications to a new pipeline stage in one call. " +
                             "Returns count of successfully updated applications.")
    @PatchMapping("/bulk-status")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<Map<String, Object>> bulkUpdateStatus(
            @Valid @RequestBody com.smarthire.api.v1.dto.request.BulkStatusUpdateRequest req,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        int updated = applicationService.bulkUpdateStatus(req.applicationIds(), req.status(), req.note(), userId);
        return ResponseEntity.ok(Map.of(
            "updated", updated,
            "message", updated + " application" + (updated == 1 ? "" : "s") + " moved to " + req.status()
        ));
    }


}