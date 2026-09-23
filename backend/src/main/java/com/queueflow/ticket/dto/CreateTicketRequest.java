package com.queueflow.ticket.dto;

import java.util.UUID;

import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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

        @NotNull(message = "creatorId is required")
        @Schema(description = "Id of the user creating the ticket. "
                + "Temporary Phase 1 identity input supplied by the client; Phase 2 authentication will "
                + "derive it from the authenticated user instead.")
        UUID creatorId,

        @Schema(description = "Optional; must be a member of the project's workspace")
        UUID assigneeId) {
}
