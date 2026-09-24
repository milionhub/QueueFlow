package com.queueflow.dashboard;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;

/**
 * The columns DashboardRepository's ticket lists select: only what a
 * DashboardTicketResponse needs, the display key still in its two parts.
 * Never a Ticket entity, so no description, labels or lazy associations
 * are loaded.
 */
public record DashboardTicketRow(
        UUID id,
        UUID projectId,
        String projectKey,
        long ticketNumber,
        String title,
        TicketStatus status,
        TicketPriority priority,
        UUID assigneeId,
        String assigneeName,
        OffsetDateTime updatedAt) {
}
