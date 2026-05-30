package com.orbix.engine.modules.fiscal.service;

/**
 * Thrown by a FiscalProvider when a non-retryable fiscalization error occurs.
 * Retryable transient errors (EFDMS outage) should be propagated as RuntimeException
 * so the outbox poller can retry; this exception signals a logic / configuration
 * fault that retrying will not fix.
 */
public class FiscalizationException extends RuntimeException {

    public FiscalizationException(String message) {
        super(message);
    }

    public FiscalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
