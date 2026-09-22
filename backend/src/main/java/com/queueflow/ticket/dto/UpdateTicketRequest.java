package com.queueflow.ticket.dto;

import java.util.UUID;

import com.queueflow.common.PatchField;
import com.queueflow.ticket.TicketPriority;
import com.queueflow.ticket.TicketStatus;

import jakarta.validation.constraints.Size;

/**
 * PATCH-style partial update for a Ticket.
 *
 * title/status/priority: null - whether the JSON key was omitted or
 * explicitly set to null - always means "do not change". The persistence
 * model requires these NOT NULL, so there is no valid "clear" state for
 * them; a plain nullable field is sufficient.
 *
 * description/assigneeId: "omitted" and "explicitly null" are different,
 * meaningful states (leave unchanged vs. clear the value), so they are
 * PatchField-typed. See {@link PatchField} for exactly how this is meant to
 * behave under Jackson deserialization, and why this class is intentionally
 * NOT a record.
 */
public class UpdateTicketRequest {

    @Size(max = 255, message = "title must be at most 255 characters")
    private String title;

    private PatchField<String> description = PatchField.undefined();

    private TicketStatus status;

    private TicketPriority priority;

    private PatchField<UUID> assigneeId = PatchField.undefined();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * Named deliberately not to match "description" as a JavaBean getter:
     * the setter below accepts a raw String while this returns a
     * PatchField<String>, and giving both the same property name would let
     * Jackson try to unify them into one (mismatched) property definition.
     * Jackson deserialization only needs the setter; this accessor is for
     * the service to read afterward.
     */
    public PatchField<String> descriptionPatch() {
        return description;
    }

    public void setDescription(String description) {
        this.description = PatchField.of(description);
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public void setPriority(TicketPriority priority) {
        this.priority = priority;
    }

    public PatchField<UUID> assigneeIdPatch() {
        return assigneeId;
    }

    public void setAssigneeId(UUID assigneeId) {
        this.assigneeId = PatchField.of(assigneeId);
    }
}
