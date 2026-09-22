package com.queueflow.label.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateLabelRequest(
        @NotNull(message = "workspaceId is required")
        UUID workspaceId,

        @NotBlank(message = "name must not be blank")
        @Size(max = 50, message = "name must be at most 50 characters")
        String name) {
}
