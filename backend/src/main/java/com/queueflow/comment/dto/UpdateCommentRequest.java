package com.queueflow.comment.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateCommentRequest(
        @NotBlank(message = "content must not be blank")
        String content) {
}
