package com.smarthire.api.v1.dto.request;
import jakarta.validation.constraints.*;
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min=8, max=128)
    @Pattern(regexp="^(?=.*[A-Z])(?=.*\\d).+$",
        message="Password must contain at least one uppercase letter and one digit.")
    String newPassword
) {}
