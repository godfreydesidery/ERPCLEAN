package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.CustomerReturnLine;

import java.math.BigDecimal;

public record CustomerReturnLineDto(
    Long id,
    Integer lineNo,
    Long itemId,
    Long uomId,
    BigDecimal returnedQty,
    BigDecimal unitPrice,
    Long vatGroupId,
    BigDecimal taxAmount,
    BigDecimal lineTotal,
    Long originalLineId
) {
    public static CustomerReturnLineDto from(CustomerReturnLine l) {
        return new CustomerReturnLineDto(l.getId(), l.getLineNo(), l.getItemId(), l.getUomId(),
            l.getReturnedQty(), l.getUnitPrice(), l.getVatGroupId(),
            l.getTaxAmount(), l.getLineTotal(), l.getOriginalLineId());
    }
}
