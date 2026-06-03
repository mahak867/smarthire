// ── SmartHire · src/main/java/com/smarthire/api/v1/dto/request/RegisterRequest.java ──
package com.smarthire.api.v1.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank @Email(message = "Please provide a valid email address.")
    String email,

    @NotBlank @Size(min = 8, max = 128,
        message = "Password must be between 8 and 128 characters.")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*\\d).+$",
        message = "Password must contain at least one uppercase letter and one digit.")
    String password,

    @NotBlank @Size(min = 1, max = 100,
        message = "First name must be between 1 and 100 characters.")
    String firstName,

    @NotBlank @Size(min = 1, max = 100,
        message = "Last name must be between 1 and 100 characters.")
    String lastName
) {}
