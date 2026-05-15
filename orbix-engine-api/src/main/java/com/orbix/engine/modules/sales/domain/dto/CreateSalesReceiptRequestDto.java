package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.ReceiptMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateSalesReceiptRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long customerId,
    @NotNull LocalDate receiptDate,
    @NotNull ReceiptMethod method,
    String reference,
    @NotBlank String currencyCode,
    @NotNull @Positive BigDecimal totalAmount,
    String notes,
    @Valid List<Allocation> allocations
) {
    public record Allocation(
        @NotNull Long salesInvoiceId,
        @NotNull @Positive BigDecimal amount
    ) {}
}
