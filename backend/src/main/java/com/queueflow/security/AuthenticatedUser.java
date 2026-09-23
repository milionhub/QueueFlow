package com.queueflow.security;

import java.util.Objects;
import java.util.UUID;

import com.queueflow.user.UserRole;

/**
 * The caller of an authenticated request, as the application sees it: who
 * they are, which workspace they belong to and their role - all read from
 * the database for that request, never from token claims.
 *
 * Deliberately minimal and free of Spring Security types, so services can
 * take it as a plain argument. No credentials, token or profile data.
 */
public record AuthenticatedUser(UUID userId, UUID workspaceId, UserRole role) {

    public AuthenticatedUser {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(workspaceId, "workspaceId must not be null");
        Objects.requireNonNull(role, "role must not be null");
    }

    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }
}
