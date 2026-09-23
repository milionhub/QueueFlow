package com.queueflow.ticket.dto;

import java.util.UUID;

import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new ticket. There is no creator field: the creator is always the
 * authenticated user making the request.
 */
public record CreateTicketRequest(
        @NotNull(message = "projectId is required")
        UUID projectId,

        @NotBlank(message = "title must not be blank")
        @Size(max = 255, message = "title must be at most 255 characters")
        String title,

        String description,

        @NotNull(message = "status is required")
        TicketStatus status,

        @NotNull(message = "priority is required")
        TicketPriority priority,

        @Schema(description = "Optional; must be a member of the project's workspace")
        UUID assigneeId) {
}
