package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.ProductionConsumption;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductionConsumptionDto(
    Long id,
    Integer lineNo,
    Long inputItemId,
    BigDecimal plannedQty,
    BigDecimal actualQty,
    Long uomId,
    BigDecimal unitCost,
    Instant postedAt,
    String notes
) {
    public static ProductionConsumptionDto from(ProductionConsumption c) {
        return new ProductionConsumptionDto(
            c.getId(),
            c.getLineNo(),
            c.getInputItemId(),
            c.getPlannedQty(),
            c.getActualQty(),
            c.getUomId(),
            c.getUnitCost(),
            c.getPostedAt(),
            c.getNotes()
        );
    }
}
