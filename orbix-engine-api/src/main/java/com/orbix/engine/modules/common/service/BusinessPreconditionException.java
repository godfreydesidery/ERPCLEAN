package com.orbix.engine.modules.common.service;

/**
 * Thrown when a business precondition is not satisfied — e.g. no open business
 * day, gift card expired, resource in the wrong lifecycle state for the
 * requested operation.
 *
 * <p>GlobalExceptionHandler maps this to HTTP 422 with the supplied
 * {@code responseCode}. Callers that want a different 4xx (e.g. 409 Conflict
 * for a duplicate-open-day) should throw {@link ResourceConflictException}.
 */
public class BusinessPreconditionException extends RuntimeException {

    private final String responseCode;

    public BusinessPreconditionException(String responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
