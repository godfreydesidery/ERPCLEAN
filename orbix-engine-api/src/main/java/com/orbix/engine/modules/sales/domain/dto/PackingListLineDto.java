package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.PackingListLine;

import java.math.BigDecimal;

public record PackingListLineDto(
    Long id,
    Long salesInvoiceLineId,
    BigDecimal qty
) {
    public static PackingListLineDto from(PackingListLine l) {
        return new PackingListLineDto(l.getId(), l.getSalesInvoiceLineId(), l.getQty());
    }
}
