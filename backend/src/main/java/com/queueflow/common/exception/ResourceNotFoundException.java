package com.queueflow.common.exception;

/**
 * Thrown by the service layer when a requested resource does not exist.
 * Deliberately a single, plain exception rather than a hierarchy - global
 * HTTP mapping (e.g. to a 404 response) belongs to a later phase.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
