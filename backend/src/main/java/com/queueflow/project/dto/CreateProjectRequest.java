package com.queueflow.project.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotNull(message = "workspaceId is required")
        UUID workspaceId,

        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "key must not be blank")
        @Size(max = 10, message = "key must be at most 10 characters")
        String key,

        String description) {
}
