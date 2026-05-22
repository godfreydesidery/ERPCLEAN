package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.entity.CustomerOrderLine;

import java.math.BigDecimal;

public record CustomerOrderLineDto(
    Long id,
    Integer lineNo,
    Long itemId,
    Long uomId,
    BigDecimal qty,
    BigDecimal unitPrice,
    BigDecimal discountAmount,
    BigDecimal lineTotal,
    String notes
) {
    public static CustomerOrderLineDto from(CustomerOrderLine line) {
        return new CustomerOrderLineDto(
            line.getId(),
            line.getLineNo(),
            line.getItemId(),
            line.getUomId(),
            line.getQty(),
            line.getUnitPrice(),
            line.getDiscountAmount(),
            line.getLineTotal(),
            line.getNotes()
        );
    }
}
