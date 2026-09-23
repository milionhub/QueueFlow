package com.queueflow.comment.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCommentRequest(
        @NotNull(message = "ticketId is required")
        UUID ticketId,

        @NotNull(message = "authorId is required")
        @Schema(description = "Id of the user writing the comment. "
                + "Temporary Phase 1 identity input supplied by the client; Phase 2 authentication will "
                + "derive it from the authenticated user instead.")
        UUID authorId,

        @NotBlank(message = "content must not be blank")
        String content) {
}
