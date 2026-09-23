package com.queueflow.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A new label. There is no workspace field: it is always created in the
 * authenticated user's workspace.
 */
public record CreateLabelRequest(
        @NotBlank(message = "name must not be blank")
        @Size(max = 50, message = "name must be at most 50 characters")
        String name) {
}
