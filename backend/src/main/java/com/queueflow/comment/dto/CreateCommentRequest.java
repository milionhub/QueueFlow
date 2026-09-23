package com.queueflow.comment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * A new comment. There is no author field: the author is always the
 * authenticated user making the request.
 */
public record CreateCommentRequest(
        @NotNull(message = "ticketId is required")
        UUID ticketId,

        @NotBlank(message = "content must not be blank")
        String content) {
}
