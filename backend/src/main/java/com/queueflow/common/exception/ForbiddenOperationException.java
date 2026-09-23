package com.queueflow.common.exception;

/**
 * Thrown by the service layer when the request is well-formed and refers to
 * existing, compatible resources, but the acting user is not allowed to
 * perform the operation - e.g. editing someone else's comment, or acting on
 * a ticket from outside its workspace. "The actor may not do this", as
 * opposed to InvalidRelationshipException ("these resources cannot be
 * related") and BusinessRuleViolationException ("this value is invalid").
 *
 * These are the existing Phase 1 service-level rules only; Phase 2
 * authentication/authorization will supply the actor from the principal.
 */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
