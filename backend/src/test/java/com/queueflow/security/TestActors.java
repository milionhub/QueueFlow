package com.queueflow.security;

import java.util.UUID;

import com.queueflow.user.User;
import com.queueflow.user.UserRole;
import com.queueflow.workspace.Workspace;

/**
 * The AuthenticatedUser the security layer builds for a persisted user, for
 * tests that call services directly instead of going through HTTP.
 */
public final class TestActors {

    private TestActors() {
    }

    public static AuthenticatedUser actorOf(User user) {
        return new AuthenticatedUser(user.getId(), user.getWorkspace().getId(), user.getRole());
    }

    /**
     * A caller belonging to the given workspace, for service calls that only
     * need the caller's workspace (creating a project or label, reading).
     */
    public static AuthenticatedUser actorIn(Workspace workspace) {
        return new AuthenticatedUser(UUID.randomUUID(), workspace.getId(), UserRole.ADMIN);
    }
}
