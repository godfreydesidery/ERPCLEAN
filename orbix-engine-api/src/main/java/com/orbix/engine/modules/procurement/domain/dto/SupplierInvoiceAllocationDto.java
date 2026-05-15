package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoiceGrn;

import java.math.BigDecimal;

public record SupplierInvoiceAllocationDto(
    Long grnId,
    BigDecimal amount
) {
    public static SupplierInvoiceAllocationDto from(SupplierInvoiceGrn alloc) {
        return new SupplierInvoiceAllocationDto(alloc.getGrnId(), alloc.getAmount());
    }
}
