package com.queueflow.common.exception;

/**
 * Thrown by the service layer when a requested resource does not exist - or
 * exists only in another workspace, which callers must not be able to tell
 * apart. Deliberately a single, plain exception rather than a hierarchy;
 * GlobalExceptionHandler maps it to 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
