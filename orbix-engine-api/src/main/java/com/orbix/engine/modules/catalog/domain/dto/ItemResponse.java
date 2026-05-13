package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;

import java.math.BigDecimal;

public record ItemResponse(
    Long id,
    String code,
    String name,
    ItemType type,
    ItemStatus status,
    BigDecimal avgCost,
    BigDecimal lastCost
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
            item.getId(),
            item.getCode(),
            item.getName(),
            item.getType(),
            item.getStatus(),
            item.getAvgCost(),
            item.getLastCost()
        );
    }
}
