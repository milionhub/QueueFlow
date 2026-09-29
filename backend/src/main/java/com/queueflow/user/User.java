package com.queueflow.user;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.queueflow.workspace.Workspace;

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

@Entity
@Table(name = "users")
public class User {

    /**
     * The password hash of a removed user. The delegating PasswordEncoder
     * knows no "{removed}" encoding, so matching against it fails (see
     * AuthService.passwordMatches): no password can ever log in again.
     */
    static final String REMOVED_PASSWORD_HASH = "{removed}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false, updatable = false)
    private Workspace workspace;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "updated_at", insertable = false, nullable = false)
    private OffsetDateTime updatedAt;

    /** Set when an ADMIN removed this user from the workspace (V6); null for a current member. */
    @Column(name = "removed_at")
    private OffsetDateTime removedAt;

    protected User() {
        // required by JPA
    }

    public User(String name, String email, String passwordHash, UserRole role, Workspace workspace) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.workspace = workspace;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getRemovedAt() {
        return removedAt;
    }

    public boolean isRemoved() {
        return removedAt != null;
    }

    /** An edit of the member's name: a no-op when unchanged, otherwise updatedAt advances. */
    public void rename(String name) {
        if (!this.name.equals(name)) {
            this.name = name;
            touch();
        }
    }

    /**
     * Removes this user from their workspace without deleting the row, which
     * tickets, comments and activities they took part in still reference
     * (see V6). The name stays, so that history still says who it was. The
     * email becomes a unique placeholder under the reserved .invalid domain,
     * which frees the real address for a new account and can never receive
     * mail; the password hash becomes one that no password matches.
     */
    public void markRemoved() {
        OffsetDateTime now = now();
        this.removedAt = now;
        this.email = "removed-" + id + "@removed.invalid";
        this.passwordHash = REMOVED_PASSWORD_HASH;
        this.updatedAt = now;
    }

    /**
     * Explicit rather than @PreUpdate, like Project: only real edits move
     * updatedAt. UTC and truncated to microseconds, matching what
     * TIMESTAMPTZ stores and reads back, so the value returned in an update
     * response is identical to a later read.
     */
    private void touch() {
        this.updatedAt = now();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public String toString() {
        return "User{id=%s, name=%s, email=%s, role=%s, workspaceId=%s}"
                .formatted(id, name, email, role, workspace != null ? workspace.getId() : null);
    }
}
