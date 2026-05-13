package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemRepository repo;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Item")
    public ItemResponseDto create(CreateItemRequestDto request) {
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
        return ItemResponseDto.from(saved);
    }
}
