package com.orbix.engine.modules.common.service;

/**
 * SPI for in-process outbox event handlers.
 *
 * <p>Implement this interface in any module that needs to consume a domain event
 * type. The {@link OutboxDispatcher} collects all Spring beans of this type and
 * routes each PENDING {@code domain_event} row to the handler whose
 * {@link #eventType()} matches the row's {@code type} column.
 *
 * <p>Rules:
 * <ul>
 *   <li>One handler per event type. If two beans claim the same type the
 *       application will fail to start (duplicate key in the map).</li>
 *   <li>Handlers must be idempotent — the outbox delivers at-least-once.</li>
 *   <li>Throw any {@link Exception} to signal failure; the dispatcher records
 *       the attempt and retries up to {@code orbix.outbox.max-attempts}.</li>
 *   <li>This interface lives in {@code common.service} so every module can
 *       implement it without a cross-module import.</li>
 * </ul>
 */
public interface DomainEventHandler {

    /**
     * The versioned event type string this handler consumes,
     * e.g. {@code "FiscalizationRequested.v1"}.
     * Must match the value passed to {@link EventPublisher#publish} exactly.
     */
    String eventType();

    /**
     * Handle the event.
     *
     * @param payloadJson the {@code domain_event.payload_json} value
     * @throws Exception on any processing failure (triggers outbox retry)
     */
    void handle(String payloadJson) throws Exception;
}
