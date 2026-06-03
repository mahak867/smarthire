// ── SmartHire · ExportController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.domain.enums.ApplicationStatus;
import com.smarthire.service.ExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/export")
@RequiredArgsConstructor
@Tag(name = "Export", description = "CSV data exports for reporting")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
public class ExportController {

    private final ExportService exportService;

    @Operation(
        summary = "Export applications as CSV",
        description = "Downloads all applications optionally filtered by job or status. " +
                      "Max 10,000 rows. File is named smarthire-applications-{date}.csv.")
    @GetMapping("/applications")
    public ResponseEntity<byte[]> exportApplications(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(required = false) ApplicationStatus status) {

        String csv      = exportService.exportApplicationsCsv(jobId, status);
        byte[] bytes    = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8); // BOM for Excel
        String filename = "smarthire-applications-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString())
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(bytes);
    }

    @Operation(
        summary = "Export hiring pipeline summary as CSV",
        description = "One row per open job with counts at each pipeline stage and average AI score.")
    @GetMapping("/pipeline")
    public ResponseEntity<byte[]> exportPipeline() {
        String csv      = exportService.exportPipelineCsv();
        byte[] bytes    = ("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8);
        String filename = "smarthire-pipeline-" + LocalDate.now() + ".csv";

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(filename).build().toString())
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(bytes);
    }
}
