// ── SmartHire · FileUploadController.java ──
package com.smarthire.api.v1.controllers;

import com.smarthire.security.JwtTokenProvider;
import com.smarthire.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Tag(name = "Files", description = "Resume upload and access")
@SecurityRequirement(name = "bearerAuth")
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final JwtTokenProvider  jwtTokenProvider;

    @Operation(
        summary = "Upload a resume",
        description = "Accepts PDF or Word documents up to 5 MB. " +
                      "Returns an object key to use in the application submission.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ResponseEntity<Map<String, String>> upload(
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails principal) {

        UUID userId = jwtTokenProvider.extractUserId(principal.getUsername());
        String objectKey = fileUploadService.uploadResume(file, userId);
        return ResponseEntity.ok(Map.of(
            "objectKey", objectKey,
            "message",   "Resume uploaded successfully."
        ));
    }

    @Operation(
        summary = "Get a temporary access URL for a resume",
        description = "Returns a pre-signed URL valid for 1 hour. RECRUITER and ADMIN only.")
    @GetMapping("/resume/{objectKey}")
    @PreAuthorize("hasAnyRole('RECRUITER','ADMIN')")
    public ResponseEntity<Map<String, String>> getResumeUrl(
            @PathVariable String objectKey) {

        String url = fileUploadService.getPresignedUrl(objectKey);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
