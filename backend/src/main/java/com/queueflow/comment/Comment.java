package com.queueflow.comment;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.queueflow.ticket.Ticket;
import com.queueflow.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "content", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, updatable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", insertable = false, nullable = false)
    private OffsetDateTime updatedAt;

    protected Comment() {
        // required by JPA
    }

    public Comment(String content, Ticket ticket, User author) {
        this.content = content;
        this.ticket = ticket;
        this.author = author;
    }

    public UUID getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Ticket getTicket() {
        return ticket;
    }

    public User getAuthor() {
        return author;
    }

    /**
     * Changes the content. Callers must validate (not blank) before calling
     * - this is a narrow mutator, not a validating setter.
     */
    public void changeContent(String content) {
        this.content = content;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Same application-managed updatedAt strategy as Ticket: updated_at has
     * a DB-side DEFAULT now() for insert time only, no DB trigger advances
     * it on UPDATE. This fires immediately before Hibernate issues an
     * UPDATE for this entity (i.e. only when changeContent() actually
     * dirtied the field), refreshing updatedAt with the real wall-clock
     * time - in UTC, truncated to microseconds, for the same TIMESTAMPTZ
     * round-trip reason as Ticket.
     */
    @PreUpdate
    private void onUpdate() {
        this.updatedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public String toString() {
        return "Comment{id=%s, ticketId=%s, authorId=%s}"
                .formatted(id, ticket != null ? ticket.getId() : null, author != null ? author.getId() : null);
    }
}
