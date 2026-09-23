package com.queueflow.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Registration of a new workspace and its first user, who always becomes the
 * workspace's ADMIN. There is deliberately no role or workspaceId here:
 * registration never joins an existing workspace.
 *
 * Bean validation only checks presence (and the password minimum, which is
 * the same before and after - passwords are never changed). Length limits
 * and email syntax apply to the normalized values, so AuthService checks
 * them after trimming/normalizing: "  Ada@Example.COM  " is a valid email.
 * The 72-byte UTF-8 password limit is also AuthService's, as bean
 * validation cannot express it.
 */
public record RegisterRequest(
        @NotBlank(message = "name must not be blank")
        @Schema(maxLength = 255, description = "Trimmed; at most 255 characters after trimming")
        String name,

        @NotBlank(message = "email must not be blank")
        @Schema(format = "email", maxLength = 320, example = "ada@example.com",
                description = "Trimmed and lower-cased; must not already be registered in any letter case")
        String email,

        @NotNull(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "At least 8 characters and at most 72 bytes when UTF-8 encoded. Used as given: "
                        + "never trimmed or otherwise changed.")
        String password,

        @NotBlank(message = "workspaceName must not be blank")
        @Schema(maxLength = 255, description = "Trimmed; at most 255 characters after trimming")
        String workspaceName) {

    /** Never prints the password: records otherwise include every component. */
    @Override
    public String toString() {
        return "RegisterRequest[name=%s, email=%s, password=<redacted>, workspaceName=%s]"
                .formatted(name, email, workspaceName);
    }
}
