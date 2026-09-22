package com.queueflow.user.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.queueflow.user.User;
import com.queueflow.user.UserRole;

/**
 * Read-only, API-facing view of a User. Deliberately excludes passwordHash
 * and never exposes the Workspace entity - only its id.
 */
public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        UUID workspaceId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getWorkspace().getId(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
