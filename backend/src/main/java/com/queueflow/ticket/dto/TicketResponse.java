package com.queueflow.ticket.dto;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.queueflow.label.Label;
import com.queueflow.label.dto.LabelResponse;
import com.queueflow.ticket.Ticket;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;
import com.queueflow.user.User;

import io.swagger.v3.oas.annotations.media.Schema;

public record TicketResponse(
        UUID id,
        long ticketNumber,
        @Schema(description = "Project key and ticket number, e.g. CORE-7", example = "CORE-7")
        String displayKey,
        String title,
        @Schema(types = {"string", "null"})
        String description,
        TicketStatus status,
        TicketPriority priority,
        UUID projectId,
        String projectKey,
        UUID creatorId,
        @Schema(types = {"string", "null"}, format = "uuid", description = "Null when the ticket is unassigned")
        UUID assigneeId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        @Schema(description = "Attached labels, ordered by name; empty when there are none")
        List<LabelResponse> labels) {

    /**
     * Same user-facing order as the workspace label list (see
     * LabelRepository.findAllInWorkspaceSortedByName): case-insensitive
     * name, then exact name for case-only ties ("Bug"/"bug"), then id
     * (compared as its canonical string, which matches PostgreSQL's uuid
     * order). Ticket.labels is a Set with no meaningful order of its own.
     */
    private static final Comparator<Label> LABEL_ORDER = Comparator
            .comparing((Label label) -> label.getName().toLowerCase(Locale.ROOT))
            .thenComparing(Label::getName)
            .thenComparing(label -> label.getId().toString());

    /** Never null: always an immutable list, [] for a ticket without labels. */
    public TicketResponse {
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    /**
     * Must be called while the Ticket's Project and labels are reachable -
     * i.e. from inside the transactional service boundary that
     * loaded/created it. getDisplayKey() initializes the Project association
     * and getLabels() the (lazy, batch-fetched) label collection if they
     * aren't already loaded. The labels are read from the current managed
     * state, so an association change made earlier in the same transaction
     * is reflected without any flush. The entity collection itself is only
     * read and sorted into a new list, never mutated.
     */
    public static TicketResponse from(Ticket ticket) {
        User assignee = ticket.getAssignee();
        List<LabelResponse> labels = ticket.getLabels().stream()
                .sorted(LABEL_ORDER)
                .map(LabelResponse::from)
                .toList();
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
                ticket.getUpdatedAt(),
                labels);
    }
}
