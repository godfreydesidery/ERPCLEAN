package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;

import java.math.BigDecimal;

public record PosSaleLineDto(
    Long id,
    Integer lineNo,
    Long itemId,
    Long uomId,
    BigDecimal qty,
    BigDecimal unitPrice,
    BigDecimal discountPct,
    BigDecimal discountAmount,
    Long vatGroupId,
    BigDecimal taxAmount,
    BigDecimal lineTotal,
    BigDecimal costAmount
) {
    public static PosSaleLineDto from(PosSaleLine l) {
        return new PosSaleLineDto(l.getId(), l.getLineNo(), l.getItemId(), l.getUomId(),
            l.getQty(), l.getUnitPrice(), l.getDiscountPct(), l.getDiscountAmount(),
            l.getVatGroupId(), l.getTaxAmount(), l.getLineTotal(), l.getCostAmount());
    }
}
