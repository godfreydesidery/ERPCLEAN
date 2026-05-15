package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.SupplierPaymentAllocation;

import java.math.BigDecimal;

public record SupplierPaymentAllocationDto(
    Long id,
    Long supplierInvoiceId,
    BigDecimal amount
) {
    public static SupplierPaymentAllocationDto from(SupplierPaymentAllocation alloc) {
        return new SupplierPaymentAllocationDto(alloc.getId(), alloc.getSupplierInvoiceId(),
            alloc.getAmount());
    }
}
