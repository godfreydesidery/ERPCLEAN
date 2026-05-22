package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;

import java.math.BigDecimal;

public record SalesInvoiceLineDto(
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
    public static SalesInvoiceLineDto from(SalesInvoiceLine line) {
        return new SalesInvoiceLineDto(
            line.getId(), line.getLineNo(), line.getItemId(), line.getUomId(),
            line.getQty(), line.getUnitPrice(), line.getDiscountPct(),
            line.getDiscountAmount(), line.getVatGroupId(), line.getTaxAmount(),
            line.getLineTotal(), line.getCostAmount()
        );
    }
}
