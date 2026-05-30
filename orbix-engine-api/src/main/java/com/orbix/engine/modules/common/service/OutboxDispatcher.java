package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.entity.DomainEvent;
import com.orbix.engine.modules.common.repository.DomainEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Polls the outbox and dispatches PENDING events to in-process listeners
 * and external webhook subscribers. At-least-once delivery; listeners must
 * be idempotent. See ARCHITECTURE.md §2.10.
 *
 * <p>In-process routing: all Spring beans that implement {@link DomainEventHandler}
 * are injected as a list. Each handler declares the event type it handles via
 * {@link DomainEventHandler#eventType()}. The dispatcher routes by type string —
 * no coupling to specific modules. Handlers in business modules (e.g. fiscal)
 * implement this common interface; common never imports them directly.
 */
@Component
public class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final DomainEventRepository repo;
    private final Map<String, DomainEventHandler> handlersByType;

    @Value("${orbix.outbox.batch-size}") private int batchSize;
    @Value("${orbix.outbox.max-attempts}") private int maxAttempts;

    public OutboxDispatcher(DomainEventRepository repo, List<DomainEventHandler> handlers) {
        this.repo = repo;
        this.handlersByType = handlers.stream()
            .collect(Collectors.toMap(DomainEventHandler::eventType, Function.identity()));
        log.info("OutboxDispatcher initialized with {} handler(s): {}",
            handlers.size(), handlersByType.keySet());
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
        DomainEventHandler handler = handlersByType.get(event.getType());
        if (handler != null) {
            log.debug("Dispatching event {} type {} to {}", event.getId(), event.getType(),
                handler.getClass().getSimpleName());
            try {
                handler.handle(event.getPayloadJson());
            } catch (Exception ex) {
                // Wrap checked exceptions so the outer catch can record the failure.
                throw new RuntimeException("Handler " + handler.getClass().getSimpleName()
                    + " failed for event " + event.getId() + ": " + ex.getMessage(), ex);
            }
        } else {
            // No handler registered — log and mark dispatched so the row doesn't block the queue.
            // Webhook delivery for external subscribers would be enqueued here in a future slice.
            log.debug("No in-process handler for event type '{}' (id={}); marking dispatched",
                event.getType(), event.getId());
        }
    }
}
