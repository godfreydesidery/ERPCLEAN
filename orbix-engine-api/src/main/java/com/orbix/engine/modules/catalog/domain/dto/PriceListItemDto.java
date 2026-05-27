package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A price row, enriched with the item / UoM codes so callers need not re-fetch them. */
public record PriceListItemDto(
    Long id,
    Long priceListId,
    Long itemId,
    String itemCode,
    String itemName,
    Long uomId,
    String uomCode,
    BigDecimal minQty,
    BigDecimal price,
    LocalDate validFrom,
    LocalDate validTo
) {
    public static PriceListItemDto of(PriceListItem row, String itemCode, String itemName, String uomCode) {
        return new PriceListItemDto(
            row.getId(),
            row.getPriceListId(),
            row.getItemId(),
            itemCode,
            itemName,
            row.getUomId(),
            uomCode,
            row.getMinQty(),
            row.getPrice(),
            row.getValidFrom(),
            row.getValidTo()
        );
    }
}
