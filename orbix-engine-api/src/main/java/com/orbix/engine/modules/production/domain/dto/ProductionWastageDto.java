package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.ProductionWastage;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductionWastageDto(
    Long id,
    Long productionBatchId,
    Long itemId,
    BigDecimal qty,
    Long uomId,
    WastageCategory category,
    String reason,
    Long recordedBy,
    Instant recordedAt
) {
    public static ProductionWastageDto from(ProductionWastage w) {
        return new ProductionWastageDto(
            w.getId(),
            w.getProductionBatchId(),
            w.getItemId(),
            w.getQty(),
            w.getUomId(),
            w.getCategory(),
            w.getReason(),
            w.getRecordedBy(),
            w.getRecordedAt()
        );
    }
}
