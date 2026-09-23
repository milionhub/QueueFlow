package com.queueflow.common.exception;

/**
 * Thrown by the service layer when the request is well-formed and refers to
 * existing, compatible resources, but the acting user is not allowed to
 * perform the operation - e.g. editing someone else's comment, or a MEMBER
 * creating a project (see RoleAccess). "The actor may not do this", as opposed to InvalidRelationshipException ("these
 * resources cannot be related") and BusinessRuleViolationException ("this
 * value is invalid").
 *
 * Only for resources inside the caller's workspace: anything outside it is
 * not found (ResourceNotFoundException), never forbidden, so a 403 cannot
 * confirm that another workspace's resource exists.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
