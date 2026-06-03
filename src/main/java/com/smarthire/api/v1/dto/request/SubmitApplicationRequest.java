package com.smarthire.api.v1.dto.request;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record SubmitApplicationRequest(
    @NotNull UUID jobId,
    @NotBlank @Size(max=1000) String resumeUrl,
    @Size(max=5000) String coverLetter
) {}
