package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;

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

    @Override
    @Transactional(readOnly = true)
    public PageDto<ItemResponseDto> listItems(ItemStatus status, Pageable pageable) {
        Long companyId = context.companyId();
        var page = status == null
            ? repo.findByCompanyId(companyId, pageable)
            : repo.findByCompanyIdAndStatus(companyId, status, pageable);
        return PageDto.of(page, ItemResponseDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemResponseDto getItem(Long itemId) {
        return ItemResponseDto.from(requireItem(itemId));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Item")
    public ItemResponseDto updateItem(Long itemId, UpdateItemRequestDto request) {
        Item item = requireItem(itemId);
        item.update(request.name(), request.shortName(), request.type(), request.itemGroupId(),
            request.uomId(), request.vatGroupId(), request.tracked(), request.minSellPrice(),
            context.userId());
        events.publish("ItemUpdated.v1", "Item", String.valueOf(item.getId()),
            Map.of("itemId", item.getId()));
        return ItemResponseDto.from(item);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "Item")
    public void archiveItem(Long itemId) {
        Item item = requireItem(itemId);
        if (item.getStatus() == ItemStatus.ARCHIVED) {
            throw new IllegalArgumentException("Item is already archived: " + itemId);
        }
        item.archive(context.userId());
        events.publish("ItemArchived.v1", "Item", String.valueOf(item.getId()),
            Map.of("itemId", item.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Item")
    public void activateItem(Long itemId) {
        Item item = requireItem(itemId);
        if (item.getStatus() == ItemStatus.ACTIVE) {
            throw new IllegalArgumentException("Item is already active: " + itemId);
        }
        item.activate(context.userId());
        events.publish("ItemActivated.v1", "Item", String.valueOf(item.getId()),
            Map.of("itemId", item.getId()));
    }

    private Item requireItem(Long itemId) {
        Item item = repo.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!item.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }
}
