// ── SmartHire · src/main/java/com/smarthire/api/v1/controllers/DashboardController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.api.v1.dto.response.*;
import com.smarthire.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Analytics and pipeline intelligence for recruiters")
@SecurityRequirement(name = "bearer")
@PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Top-level hiring stats")
    public ResponseEntity<DashboardStatsResponse> getStats() {
        return ResponseEntity.ok(dashboardService.getStats());
    }

    @GetMapping("/pipeline")
    @Operation(summary = "Application count grouped by pipeline status")
    public ResponseEntity<Map<String, Long>> getPipeline() {
        return ResponseEntity.ok(dashboardService.getPipelineBreakdown());
    }

    @GetMapping("/top-candidates")
    @Operation(summary = "Top 10 candidates by AI score across all open jobs")
    public ResponseEntity<List<ApplicationResponse>> getTopCandidates() {
        return ResponseEntity.ok(dashboardService.getTopCandidates(10));
    }

    @GetMapping("/hiring-funnel")
    @Operation(summary = "APPLIED → OFFERED conversion rates")
    public ResponseEntity<List<Map<String, Object>>> getHiringFunnel() {
        return ResponseEntity.ok(dashboardService.getHiringFunnel());
    }
}
