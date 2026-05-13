package com.orbix.engine.modules.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbix.engine.modules.common.domain.entity.DomainEvent;
import com.orbix.engine.modules.common.repository.DomainEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventPublisherImpl implements EventPublisher {

    private final DomainEventRepository repo;
    private final RequestContext context;
    private final ObjectMapper mapper;

    @Override
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
