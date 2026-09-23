package com.queueflow.project;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.queueflow.workspace.Workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "key", nullable = false, length = 10)
    private String key;

    @Column(name = "description")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    private Workspace workspace;

    @Generated(event = EventType.INSERT)
    @Column(name = "next_ticket_number", insertable = false, nullable = false)
    private long nextTicketNumber;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", insertable = false, nullable = false)
    private OffsetDateTime updatedAt;

    protected Project() {
        // required by JPA
    }

    public Project(String name, String key, String description, Workspace workspace) {
        this.name = name;
        this.key = key;
        this.description = description;
        this.workspace = workspace;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /**
     * Changes the name. Callers must validate (trimmed, not blank, within
     * length) before calling - this is a narrow mutator, not a validating
     * setter. A no-op when the value is unchanged.
     */
    public void changeName(String name) {
        if (!Objects.equals(this.name, name)) {
            this.name = name;
            touch();
        }
    }

    /**
     * The key is immutable after creation (it prefixes every ticket's
     * display key, e.g. ECOM-7), so there is deliberately no mutator.
     */
    public String getKey() {
        return key;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Pass null to clear. A no-op when the value is unchanged.
     */
    public void changeDescription(String description) {
        if (!Objects.equals(this.description, description)) {
            this.description = description;
            touch();
        }
    }

    /**
     * Advances updatedAt for a real project edit. Deliberately explicit
     * rather than a @PreUpdate callback (as Ticket/Comment use): the
     * projects row is also UPDATEd by every ticket creation
     * (allocateNextTicketNumber), and allocating a ticket number is not an
     * edit of the project - a @PreUpdate would bump updatedAt on every new
     * ticket. UTC and truncated to microseconds, matching what TIMESTAMPTZ
     * stores and reads back, so the in-memory value returned in an update
     * response is identical to a later read (same as Ticket/Comment).
     */
    private void touch() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public long getNextTicketNumber() {
        return nextTicketNumber;
    }

    /**
     * Allocates the next ticket number for this project: returns the
     * current counter value and advances it by one. Deliberately not a
     * public setter - the counter must only ever move forward one step at a
     * time through this method. Callers must have loaded this Project under
     * a pessimistic write lock (see ProjectRepository.findByIdAndWorkspaceIdForUpdate) in
     * an active transaction; this method itself performs no locking.
     */
    public long allocateNextTicketNumber() {
        if (nextTicketNumber < 1) {
            throw new IllegalStateException(
                    "Project " + id + " has an invalid next_ticket_number: " + nextTicketNumber);
        }
        long allocated = nextTicketNumber;
        nextTicketNumber++;
        return allocated;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Project{id=%s, key=%s, name=%s, workspaceId=%s}"
                .formatted(id, key, name, workspace != null ? workspace.getId() : null);
    }
}
