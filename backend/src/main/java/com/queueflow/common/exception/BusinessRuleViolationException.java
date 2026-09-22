package com.queueflow.common.exception;

/**
 * Thrown by the service layer when a request violates a domain/business
 * invariant that isn't a simple "not found" or "already exists" case (e.g.
 * a creator/assignee not belonging to the same workspace as the project).
 * Deliberately one general-purpose exception rather than a class per rule.
 */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
