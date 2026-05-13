package com.orbix.engine.platform.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbix.engine.platform.security.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a domain event by inserting an outbox row in the current transaction.
 * If the surrounding business transaction rolls back, the event row rolls back too —
 * which is what we want.
 */
@Service
public class EventPublisher {

    private final DomainEventRepository repo;
    private final RequestContext context;
    private final ObjectMapper mapper;

    public EventPublisher(DomainEventRepository repo, RequestContext context, ObjectMapper mapper) {
        this.repo = repo;
        this.context = context;
        this.mapper = mapper;
    }

    /**
     * @param type versioned event type, e.g. "SalesInvoicePosted.v1"
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String type, String aggregateType, String aggregateId, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialise event payload for " + type, e);
        }
        DomainEvent event = new DomainEvent(
            type, aggregateType, aggregateId, json,
            context.companyId(), context.branchId(), context.userId()
        );
        repo.save(event);
    }
}
