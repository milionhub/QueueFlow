package com.queueflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new project. There is no workspace field: it is always created in the
 * authenticated user's workspace.
 */
public record CreateProjectRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @NotBlank(message = "key must not be blank")
        @Size(max = 10, message = "key must be at most 10 characters")
        String key,

        String description) {
}
