package com.queueflow.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * An ADMIN editing a member of their own workspace. Only the name can
 * change: there is deliberately no email, password, role or workspaceId
 * (unknown JSON fields such as "role" are ignored). The name follows the
 * same rules as registration and member creation (UserAccountRules).
 */
public record UpdateMemberRequest(
        @NotBlank(message = "name must not be blank")
        @Schema(maxLength = 255, description = "Trimmed; at most 255 characters after trimming")
        String name) {
}
