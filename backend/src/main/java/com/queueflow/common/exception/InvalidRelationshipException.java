package com.queueflow.common.exception;

/**
 * Thrown by the service layer when a request tries to connect resources that
 * cannot legally be related in the QueueFlow domain model - e.g. assigning a
 * ticket to a user from another workspace, or attaching another workspace's
 * label. Every referenced resource exists; the combination is what is
 * invalid. Distinct from ForbiddenOperationException, which is about what
 * the acting user may do.
 */
public class InvalidRelationshipException extends RuntimeException {

    public InvalidRelationshipException(String message) {
        super(message);
    }
}
