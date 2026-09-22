package com.queueflow.ticket.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;

public record TicketResponse(
        UUID id,
        long ticketNumber,
        String displayKey,
        String title,
        String description,
        TicketStatus status,
        TicketPriority priority,
        UUID projectId,
        String projectKey,
        UUID creatorId,
        UUID assigneeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    /**
     * Must be called while the Ticket's Project (and, transitively, its
     * key) is reachable - i.e. from inside the transactional service
     * boundary that loaded/created it. getDisplayKey() initializes the
     * Project association if it isn't already loaded.
     */
    public static TicketResponse from(Ticket ticket) {
        User assignee = ticket.getAssignee();
        return new TicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getDisplayKey(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                ticket.getProject().getId(),
                ticket.getProject().getKey(),
                ticket.getCreator().getId(),
                assignee != null ? assignee.getId() : null,
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
