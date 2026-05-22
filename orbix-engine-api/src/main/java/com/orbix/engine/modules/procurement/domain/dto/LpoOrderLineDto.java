package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;

import java.math.BigDecimal;

public record LpoOrderLineDto(
    Long id,
    Integer lineNo,
    Long itemId,
    Long uomId,
    BigDecimal orderedQty,
    BigDecimal receivedQty,
    BigDecimal unitPrice,
    Long vatGroupId,
    BigDecimal discountPct,
    BigDecimal lineTotal
) {
    public static LpoOrderLineDto from(LpoOrderLine line) {
        return new LpoOrderLineDto(
            line.getId(),
            line.getLineNo(),
            line.getItemId(),
            line.getUomId(),
            line.getOrderedQty(),
            line.getReceivedQty(),
            line.getUnitPrice(),
            line.getVatGroupId(),
            line.getDiscountPct(),
            line.getLineTotal()
        );
    }
}
