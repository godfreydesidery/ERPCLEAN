package com.orbix.engine.modules.common.service;

/**
 * Thrown when a request conflicts with the current resource state — e.g.
 * attempting to open a second business day for a branch that already has an
 * open one (HTTP 409 Conflict).
 *
 * <p>GlobalExceptionHandler maps this to HTTP 409 with
 * {@link com.orbix.engine.modules.common.domain.enums.ResponseCode#CONFLICT}.
 */
public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String message) {
        super(message);
    }
}
