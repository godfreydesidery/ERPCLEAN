package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceListItemDto(
    Long id,
    Long priceListId,
    Long itemId,
    Long uomId,
    BigDecimal price,
    LocalDate validFrom,
    LocalDate validTo
) {
    public static PriceListItemDto from(PriceListItem item) {
        return new PriceListItemDto(
            item.getId(),
            item.getPriceListId(),
            item.getItemId(),
            item.getUomId(),
            item.getPrice(),
            item.getValidFrom(),
            item.getValidTo()
        );
    }
}
