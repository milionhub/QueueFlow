package com.queueflow.ticket.dto;

import java.util.UUID;

import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;

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
        UUID creatorId,

        UUID assigneeId) {
}
