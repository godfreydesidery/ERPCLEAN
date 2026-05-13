package com.orbix.engine.catalog.api.dto;

import com.orbix.engine.catalog.domain.Item;

import java.math.BigDecimal;

public record ItemResponse(
    Long id,
    String code,
    String name,
    Item.Type type,
    Item.Status status,
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
