package com.queueflow.security;

import com.queueflow.user.User;

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
}
