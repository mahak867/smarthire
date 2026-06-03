package com.smarthire.api.v1.dto.request;
import jakarta.validation.constraints.NotBlank;
public record UpdateApplicationStatusRequest(
    @NotBlank String status,
    String notes
) {}
