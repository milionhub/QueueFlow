package com.queueflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An ADMIN adding a member to their own workspace. There is deliberately no
 * role and no workspaceId: a created member is always a MEMBER of the
 * caller's workspace (unknown JSON fields such as "role" are ignored).
 *
 * The same presence checks as RegisterRequest; length limits, email syntax
 * and the 72-byte password limit are applied to the normalized values by
 * UserAccountRules, exactly as for registration.
 */
public record CreateMemberRequest(
        @NotBlank(message = "name must not be blank")
        @Schema(maxLength = 255, description = "Trimmed; at most 255 characters after trimming")
        String name,

        @NotBlank(message = "email must not be blank")
        @Schema(format = "email", maxLength = 320, example = "pedro@example.com",
                description = "Trimmed and lower-cased; must not already be registered in any letter case")
        String email,

        @NotNull(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        @Schema(format = "password", accessMode = Schema.AccessMode.WRITE_ONLY,
                description = "At least 8 characters and at most 72 bytes when UTF-8 encoded. Used as given: "
                        + "never trimmed or otherwise changed.")
        String password) {

    /** Never prints the password: records otherwise include every component. */
    @Override
    public String toString() {
        return "CreateMemberRequest[name=%s, email=%s, password=<redacted>]".formatted(name, email);
    }
}
