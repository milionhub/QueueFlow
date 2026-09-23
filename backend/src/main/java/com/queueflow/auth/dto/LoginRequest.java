package com.queueflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Login credentials. Only presence is validated: format or length rules here
 * would only tell a caller something about which accounts can exist. The
 * password may be any non-empty value (all-space passwords are legal at
 * registration), and is compared exactly as given.
 */
public record LoginRequest(
        @NotBlank(message = "email must not be blank")
        @Schema(format = "email", example = "ada@example.com")
        String email,

        @NotEmpty(message = "password is required")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password) {

    /** Never prints the password: records otherwise include every component. */
    @Override
    public String toString() {
        return "LoginRequest[email=%s, password=<redacted>]".formatted(email);
    }
}
