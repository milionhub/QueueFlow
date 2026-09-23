package com.queueflow.common.exception;

/**
 * Thrown by the service layer when a requested business value or state is
 * invalid even though the request itself is well-formed - e.g. a blank
 * title, an over-long name or a project key outside the V1 key format.
 * Deliberately one general-purpose exception rather than a class per rule.
 *
 * Not for permission failures (ForbiddenOperationException) or for
 * combining resources that cannot be related, such as a cross-workspace
 * assignee (InvalidRelationshipException).
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
