package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import org.springframework.data.domain.Pageable;

/**
 * Application service for the {@code Item} aggregate. Orchestrates the
 * domain + repository + cross-cutting concerns (audit, events). No HTTP,
 * no JPA specifics in the contract.
 */
public interface ItemService {

    ItemResponseDto create(CreateItemRequestDto request);

    /** Company-scoped paged list; {@code status} optional filter. */
    PageDto<ItemResponseDto> listItems(ItemStatus status, Pageable pageable);

    ItemResponseDto getItem(Long itemId);

    ItemResponseDto updateItem(Long itemId, UpdateItemRequestDto request);

    /** Soft-delete: status -> ARCHIVED. */
    void archiveItem(Long itemId);

    /** Un-archive: status -> ACTIVE. */
    void activateItem(Long itemId);
}
