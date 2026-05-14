package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;

import java.math.BigDecimal;

public record ItemResponseDto(
    Long id,
    Long companyId,
    String code,
    String name,
    String shortName,
    ItemType type,
    Long itemGroupId,
    Long uomId,
    Long vatGroupId,
    boolean tracked,
    BigDecimal avgCost,
    BigDecimal lastCost,
    BigDecimal minSellPrice,
    ItemStatus status
) {
    public static ItemResponseDto from(Item item) {
        return new ItemResponseDto(
            item.getId(),
            item.getCompanyId(),
            item.getCode(),
            item.getName(),
            item.getShortName(),
            item.getType(),
            item.getItemGroupId(),
            item.getUomId(),
            item.getVatGroupId(),
            item.isTracked(),
            item.getAvgCost(),
            item.getLastCost(),
            item.getMinSellPrice(),
            item.getStatus()
        );
    }
}
