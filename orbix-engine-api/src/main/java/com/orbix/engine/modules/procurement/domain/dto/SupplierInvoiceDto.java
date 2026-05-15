package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoiceGrn;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SupplierInvoiceDto(
    Long id,
    String number,
    String supplierInvoiceNo,
    Long companyId,
    Long branchId,
    Long supplierId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currencyCode,
    BigDecimal subtotalAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    SupplierInvoiceStatus status,
    Instant postedAt,
    Long postedBy,
    String notes,
    List<SupplierInvoiceAllocationDto> allocations
) {
    public static SupplierInvoiceDto from(SupplierInvoice inv, List<SupplierInvoiceGrn> allocs) {
        return new SupplierInvoiceDto(
            inv.getId(),
            inv.getNumber(),
            inv.getSupplierInvoiceNo(),
            inv.getCompanyId(),
            inv.getBranchId(),
            inv.getSupplierId(),
            inv.getInvoiceDate(),
            inv.getDueDate(),
            inv.getCurrencyCode(),
            inv.getSubtotalAmount(),
            inv.getTaxAmount(),
            inv.getTotalAmount(),
            inv.getPaidAmount(),
            inv.getStatus(),
            inv.getPostedAt(),
            inv.getPostedBy(),
            inv.getNotes(),
            allocs.stream().map(SupplierInvoiceAllocationDto::from).toList()
        );
    }
}
