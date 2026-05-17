package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;

import java.math.BigDecimal;

/**
 * Outgoing item representation. Carries both {@code uid} (for URLs and
 * cross-system references) and {@code id} (numeric handle for body-level
 * joins). URLs MUST address items by uid — id appears in the body only.
 *
 * <p>Long fields named {@code id} or ending in {@code Id} serialise as
 * JSON strings via {@code IdLongAsStringSerializerModifier} (JSON:API
 * discipline). Java types stay {@code Long}; Jackson coerces back from
 * {@code "42"} to {@code 42L} on deserialisation by default.
 */
public record ItemResponseDto(
    Long id,
    String uid,
    Long companyId,
    String code,
    String name,
    String shortName,
    ItemType type,
    Long itemGroupId,
    Long uomId,
    Long vatGroupId,
    boolean tracked,
    boolean weighed,
    WeighingUnit weighingUnit,
    boolean batchTracked,
    BigDecimal avgCost,
    BigDecimal lastCost,
    BigDecimal minSellPrice,
    ItemStatus status
) {
    public static ItemResponseDto from(Item item) {
        return new ItemResponseDto(
            item.getId(),
            item.getUid(),
            item.getCompanyId(),
            item.getCode(),
            item.getName(),
            item.getShortName(),
            item.getType(),
            item.getItemGroupId(),
            item.getUomId(),
            item.getVatGroupId(),
            item.isTracked(),
            item.isWeighed(),
            item.getWeighingUnit(),
            item.isBatchTracked(),
            item.getAvgCost(),
            item.getLastCost(),
            item.getMinSellPrice(),
            item.getStatus()
        );
    }
}
