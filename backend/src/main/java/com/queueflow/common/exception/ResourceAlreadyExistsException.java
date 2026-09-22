package com.queueflow.common.exception;

/**
 * Thrown by the service layer when a request would create a resource that
 * already exists (a business-level conflict). This is a pre-check for a
 * clean error on the normal path only - it is not the concurrency
 * guarantee. The relevant database UNIQUE constraint remains the final,
 * concurrency-safe protection against the same conflict under a race.
 */
public class ResourceAlreadyExistsException extends RuntimeException {

    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
