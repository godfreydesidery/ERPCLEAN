package com.orbix.engine.modules.common.service;

/**
 * Publishes a domain event by inserting an outbox row in the current
 * transaction. If the surrounding business transaction rolls back, the
 * event row rolls back too — which is what we want.
 */
public interface EventPublisher {

    /**
     * @param type versioned event type, e.g. {@code "SalesInvoicePosted.v1"}
     * @param aggregateType e.g. {@code "SalesInvoice"}
     * @param aggregateId stringified id of the aggregate root
     * @param payload arbitrary object; serialised to JSON by the implementation
     */
    void publish(String type, String aggregateType, String aggregateId, Object payload);
}
