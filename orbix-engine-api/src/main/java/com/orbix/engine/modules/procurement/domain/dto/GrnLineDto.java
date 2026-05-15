package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.GrnLine;

import java.math.BigDecimal;
import java.time.LocalDate;

public record GrnLineDto(
    Long id,
    Long lpoOrderLineId,
    Long itemId,
    Long uomId,
    BigDecimal receivedQty,
    BigDecimal unitCost,
    Long vatGroupId,
    BigDecimal lineTotal,
    String batchNo,
    LocalDate expiryDate
) {
    public static GrnLineDto from(GrnLine line) {
        return new GrnLineDto(
            line.getId(),
            line.getLpoOrderLineId(),
            line.getItemId(),
            line.getUomId(),
            line.getReceivedQty(),
            line.getUnitCost(),
            line.getVatGroupId(),
            line.getLineTotal(),
            line.getBatchNo(),
            line.getExpiryDate()
        );
    }
}
