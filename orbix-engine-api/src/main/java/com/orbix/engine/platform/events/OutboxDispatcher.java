package com.orbix.engine.platform.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Polls the outbox and dispatches PENDING events to in-process listeners
 * and external webhook subscribers. At-least-once delivery; listeners must
 * be idempotent. See ARCHITECTURE.md §2.10.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final DomainEventRepository repo;

    @Value("${orbix.outbox.batch-size}") private int batchSize;
    @Value("${orbix.outbox.max-attempts}") private int maxAttempts;

    public OutboxDispatcher(DomainEventRepository repo) {
        this.repo = repo;
    }

    @Scheduled(fixedDelayString = "${orbix.outbox.dispatch-interval-ms}")
    @Transactional
    public void dispatchBatch() {
        List<DomainEvent> batch = repo.findByStatusOrderByOccurredAtAsc(
            DomainEvent.Status.PENDING, PageRequest.of(0, batchSize)
        );
        for (DomainEvent event : batch) {
            try {
                deliver(event);
                event.markDispatched();
            } catch (Exception ex) {
                event.recordFailure(ex.getMessage());
                if (event.getAttemptCount() >= maxAttempts) {
                    event.markDeadLettered();
                    log.error("Event {} dead-lettered after {} attempts", event.getId(), maxAttempts, ex);
                }
            }
        }
    }

    private void deliver(DomainEvent event) {
        // TODO: invoke in-process listeners by type, then enqueue webhook deliveries.
        log.debug("Dispatching event {} type {}", event.getId(), event.getType());
    }
}
