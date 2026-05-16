package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.ProductionOutput;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record ProductionOutputDto(
    Long id,
    Integer lineNo,
    Long outputItemId,
    BigDecimal qty,
    Long uomId,
    BigDecimal unitCost,
    boolean primary,
    boolean packByWeight,
    Long batchId,
    String batchNo,
    LocalDate manufacturedAt,
    LocalDate expiryAt,
    Instant postedAt,
    String notes
) {
    public static ProductionOutputDto from(ProductionOutput o) {
        return new ProductionOutputDto(
            o.getId(),
            o.getLineNo(),
            o.getOutputItemId(),
            o.getQty(),
            o.getUomId(),
            o.getUnitCost(),
            o.isPrimary(),
            o.isPackByWeight(),
            o.getBatchId(),
            o.getBatchNo(),
            o.getManufacturedAt(),
            o.getExpiryAt(),
            o.getPostedAt(),
            o.getNotes()
        );
    }
}
