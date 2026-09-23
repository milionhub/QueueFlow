package com.queueflow.workspace;

import java.util.UUID;

import com.queueflow.common.exception.ResourceNotFoundException;
import com.queueflow.security.AuthenticatedUser;

/**
 * The one check for a workspace id that arrives as a resource identifier
 * (e.g. /api/workspaces/{workspaceId}/members): it must be the caller's own
 * workspace. Any other id - another tenant's or one that does not exist -
 * gets the same "Workspace not found" answer, decided before any database
 * access, so the response never reveals whether that workspace exists.
 */
public final class WorkspaceAccess {

    private WorkspaceAccess() {
    }

    public static void requireOwnWorkspace(AuthenticatedUser actor, UUID workspaceId) {
        if (!actor.workspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Workspace not found: " + workspaceId);
        }
    }
}
