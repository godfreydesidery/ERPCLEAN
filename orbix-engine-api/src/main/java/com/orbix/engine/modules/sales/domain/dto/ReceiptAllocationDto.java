package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.ReceiptAllocation;

import java.math.BigDecimal;
import java.time.Instant;

public record ReceiptAllocationDto(
    Long id,
    Long salesInvoiceId,
    BigDecimal amount,
    Instant allocatedAt,
    Long allocatedBy
) {
    public static ReceiptAllocationDto from(ReceiptAllocation a) {
        return new ReceiptAllocationDto(a.getId(), a.getSalesInvoiceId(), a.getAmount(),
            a.getAllocatedAt(), a.getAllocatedBy());
    }
}
