package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.VendorReturnLine;

import java.math.BigDecimal;

public record VendorReturnLineDto(
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
    public static VendorReturnLineDto from(VendorReturnLine l) {
        return new VendorReturnLineDto(l.getId(), l.getLineNo(), l.getItemId(), l.getUomId(),
            l.getReturnedQty(), l.getUnitPrice(), l.getVatGroupId(),
            l.getTaxAmount(), l.getLineTotal(), l.getOriginalLineId());
    }
}
