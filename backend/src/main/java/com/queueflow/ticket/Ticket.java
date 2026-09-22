package com.queueflow.ticket;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.queueflow.label.Label;
import com.queueflow.project.Project;
import com.queueflow.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "ticket_number", nullable = false, updatable = false)
    private long ticketNumber;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TicketStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private TicketPriority priority;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false, updatable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id", updatable = true)
    private User assignee;

    // Ticket owns the association: labels are managed from the ticket side
    // ("attach/detach a label on this ticket"), so only Ticket declares the
    // @JoinTable and Label carries no inverse @ManyToMany back-reference.
    // No cascade: labels are independent workspace resources that must
    // already exist before being attached, and detaching or deleting a
    // ticket must never delete the Label itself.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ticket_labels",
            joinColumns = @JoinColumn(name = "ticket_id"),
            inverseJoinColumns = @JoinColumn(name = "label_id"))
    private Set<Label> labels = new LinkedHashSet<>();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", insertable = false, nullable = false)
    private OffsetDateTime updatedAt;

    protected Ticket() {
        // required by JPA
    }

    public Ticket(long ticketNumber, String title, String description, TicketStatus status,
            TicketPriority priority, Project project, User creator, User assignee) {
        this.ticketNumber = ticketNumber;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.project = project;
        this.creator = creator;
        this.assignee = assignee;
    }

    public UUID getId() {
        return id;
    }

    public long getTicketNumber() {
        return ticketNumber;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public Project getProject() {
        return project;
    }

    public User getCreator() {
        return creator;
    }

    public User getAssignee() {
        return assignee;
    }

    /**
     * Changes the title. Callers must validate (not blank, within length)
     * before calling - this is a narrow mutator, not a validating setter.
     */
    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeDescription(String description) {
        this.description = description;
    }

    public void changeStatus(TicketStatus status) {
        this.status = status;
    }

    public void changePriority(TicketPriority priority) {
        this.priority = priority;
    }

    /**
     * Pass null to unassign.
     */
    public void changeAssignee(User assignee) {
        this.assignee = assignee;
    }

    /**
     * Read-only view: callers must go through addLabel/removeLabel to
     * mutate the association, so those methods stay the single source of
     * truth for idempotency and (later, in Phase 1.6F) change detection.
     */
    public Set<Label> getLabels() {
        return Collections.unmodifiableSet(labels);
    }

    /**
     * Attaches a label. Idempotent: attaching a label already present is a
     * no-op. Membership is checked by id, not by Label.equals()/Set
     * semantics - Label intentionally keeps default (identity) equality
     * (see class-level design note below), and two Label instances
     * representing the same row are not always the same Java object (e.g.
     * one loaded fresh vs. one already present in this collection from an
     * earlier lazy load). Returns whether the Set actually changed, so a
     * later phase can decide whether to record a LABEL_ADDED activity.
     */
    public boolean addLabel(Label label) {
        boolean alreadyPresent = labels.stream().anyMatch(existing -> existing.getId().equals(label.getId()));
        if (alreadyPresent) {
            return false;
        }
        labels.add(label);
        return true;
    }

    /**
     * Detaches a label. Idempotent: removing a label that isn't attached is
     * a no-op. Same id-based comparison as addLabel, for the same reason.
     * Returns whether the Set actually changed.
     */
    public boolean removeLabel(Label label) {
        return labels.removeIf(existing -> existing.getId().equals(label.getId()));
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Human-readable key such as "ECOM-7", derived from the owning project's
     * key and this ticket's number. Never persisted. Reading it initializes
     * the (possibly lazy) project association, so call it deliberately -
     * never from toString().
     */
    @Transient
    public String getDisplayKey() {
        return project.getKey() + "-" + ticketNumber;
    }

    /**
     * updated_at has a DB-side DEFAULT now() for insert time, but no DB
     * trigger advances it on UPDATE (and none is being added here). This
     * JPA lifecycle callback is the application-managed equivalent: it
     * fires immediately before Hibernate issues an UPDATE for this entity
     * (i.e. whenever a change* method actually dirtied a field), refreshing
     * updatedAt with the real wall-clock time. It does not fire, and
     * updatedAt does not change, when nothing was actually modified.
     */
    @PreUpdate
    private void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    @Override
    public String toString() {
        return "Ticket{id=%s, ticketNumber=%d, title=%s, status=%s, priority=%s, projectId=%s, creatorId=%s, assigneeId=%s}"
                .formatted(id, ticketNumber, title, status, priority,
                        project != null ? project.getId() : null,
                        creator != null ? creator.getId() : null,
                        assignee != null ? assignee.getId() : null);
    }
}
