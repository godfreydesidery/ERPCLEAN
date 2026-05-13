package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;

/**
 * Application service for the {@code Item} aggregate. Orchestrates the
 * domain + repository + cross-cutting concerns (audit, events). No HTTP,
 * no JPA specifics in the contract.
 */
public interface ItemService {

    ItemResponseDto create(CreateItemRequestDto request);
}
