package com.smarthire.api.v1.dto.request;
import jakarta.validation.constraints.*;
public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password
) {}
