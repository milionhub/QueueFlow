package com.queueflow.user;

import java.util.UUID;

/**
 * The three columns authentication needs about a user, read by one
 * primary-key query without loading the entity or its workspace.
 */
public record UserIdentity(UUID id, UUID workspaceId, UserRole role) {
}
