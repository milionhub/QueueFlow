package com.queueflow.activity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.queueflow.ticket.Ticket;
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An immutable, append-only audit record. Unlike every other entity in this
 * codebase, it has no updatedAt field and no setters at all - activity
 * history is never edited after the fact, only ever created.
 */
@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private ActivityType type;

    @Column(name = "old_value", updatable = false)
    private String oldValue;

    @Column(name = "new_value", updatable = false)
    private String newValue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    protected Activity() {
        // required by JPA
    }

    public Activity(ActivityType type, String oldValue, String newValue, Ticket ticket, User user) {
        this.type = type;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.ticket = ticket;
        this.user = user;
    }

    public UUID getId() {
        return id;
    }

    public ActivityType getType() {
        return type;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getUser() {
        return user;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Activity{id=%s, type=%s, ticketId=%s, userId=%s}"
                .formatted(id, type, ticket != null ? ticket.getId() : null, user != null ? user.getId() : null);
    }
}
