package com.queueflow.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.dashboard.DashboardTicketRow;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A ticket as the dashboard lists it: enough to recognize and rank it,
 * nothing more - no description, labels or creator.
 */
public record DashboardTicketResponse(
        UUID id,
        UUID projectId,
        @Schema(description = "Project key and ticket number, e.g. CORE-7", example = "CORE-7")
        String displayKey,
        String title,
        TicketStatus status,
        TicketPriority priority,
        @Schema(types = {"string", "null"}, format = "uuid", description = "Null when the ticket is unassigned")
        UUID assigneeId,
        @Schema(types = {"string", "null"}, description = "Null when the ticket is unassigned")
        String assigneeName,
        OffsetDateTime updatedAt) {

    /** The display key is assembled exactly as Ticket.getDisplayKey() does: project key, "-", number. */
    public static DashboardTicketResponse from(DashboardTicketRow row) {
        return new DashboardTicketResponse(
                row.id(),
                row.projectId(),
                row.projectKey() + "-" + row.ticketNumber(),
                row.title(),
                row.status(),
                row.priority(),
                row.assigneeId(),
                row.assigneeName(),
                row.updatedAt());
    }
}
