package com.orbix.engine.modules.procurement.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateSupplierInvoiceRequestDto(
    @NotBlank String number,
    @NotBlank String supplierInvoiceNo,
    @NotNull Long branchId,
    @NotNull Long supplierId,
    @NotNull LocalDate invoiceDate,
    /** Optional override; defaults to {@code invoiceDate + supplier.paymentTermsDays}. */
    LocalDate dueDate,
    @NotBlank String currencyCode,
    @NotNull @PositiveOrZero BigDecimal subtotalAmount,
    @NotNull @PositiveOrZero BigDecimal taxAmount,
    String notes,
    @NotEmpty @Valid List<Allocation> allocations
) {
    public record Allocation(
        @NotNull Long grnId,
        @NotNull @Positive BigDecimal amount
    ) {}
}
