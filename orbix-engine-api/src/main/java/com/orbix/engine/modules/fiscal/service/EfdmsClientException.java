package com.orbix.engine.modules.fiscal.service;

/**
 * Thrown by EfdmsClient implementations when the EFDMS call fails.
 *
 * The outbox poller treats any RuntimeException from the handler as retryable;
 * implementations should throw this for transient errors (EFDMS outage, timeout)
 * so the outbox will retry. For non-retryable logic errors, throw
 * FiscalizationException instead.
 */
public class EfdmsClientException extends RuntimeException {

    public EfdmsClientException(String message) {
        super(message);
    }

    public EfdmsClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
