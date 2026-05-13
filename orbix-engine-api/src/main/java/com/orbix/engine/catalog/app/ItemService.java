package com.orbix.engine.catalog.app;

import com.orbix.engine.catalog.api.dto.CreateItemRequest;
import com.orbix.engine.catalog.api.dto.ItemResponse;
import com.orbix.engine.catalog.domain.Item;
import com.orbix.engine.catalog.infra.ItemRepository;
import com.orbix.engine.platform.audit.Auditable;
import com.orbix.engine.platform.events.EventPublisher;
import com.orbix.engine.platform.security.RequestContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Application service for the Item aggregate. Hexagonal core: orchestrates
 * domain + repository + cross-cutting (audit, events). No HTTP, no JPA-specifics.
 */
@Service
public class ItemService {

    private final ItemRepository repo;
    private final EventPublisher events;
    private final RequestContext context;

    public ItemService(ItemRepository repo, EventPublisher events, RequestContext context) {
        this.repo = repo;
        this.events = events;
        this.context = context;
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Item")
    public ItemResponse create(CreateItemRequest request) {
        Long companyId = context.companyId();
        repo.findByCompanyAndCode(companyId, request.code()).ifPresent(existing -> {
            throw new IllegalArgumentException("Item code already exists: " + request.code());
        });
        Item item = new Item(
            companyId,
            request.code(),
            request.name(),
            request.type(),
            request.itemGroupId(),
            request.uomId(),
            request.vatGroupId(),
            context.userId()
        );
        Item saved = repo.save(item);
        events.publish(
            "ItemCreated.v1",
            "Item",
            String.valueOf(saved.getId()),
            Map.of("itemId", saved.getId(), "code", saved.getCode(), "companyId", companyId)
        );
        return ItemResponse.from(saved);
    }
}
