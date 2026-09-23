package com.queueflow.security;

import com.queueflow.common.exception.ForbiddenOperationException;

/**
 * The one check for ADMIN-only operations (creating and updating projects,
 * creating members). Called by the services - the trust boundary for every
 * business rule - so no controller, present or future, can reach these
 * operations without it, and it is decided from the caller's current
 * database role (AuthenticatedUser), never a token claim.
 *
 * Deliberately a service-level check rather than @PreAuthorize: an
 * operation on an existing resource must first resolve that resource in the
 * caller's workspace, so a MEMBER asking about another workspace's project
 * gets the same 404 as anyone else, and only a same-workspace role failure
 * becomes this 403.
 */
public final class RoleAccess {

    private RoleAccess() {
    }

    /** @param action completes "Only workspace admins can ...", e.g. "create projects" */
    public static void requireAdmin(AuthenticatedUser actor, String action) {
        if (!actor.isAdmin()) {
            throw new ForbiddenOperationException("Only workspace admins can " + action);
        }
    }
}
