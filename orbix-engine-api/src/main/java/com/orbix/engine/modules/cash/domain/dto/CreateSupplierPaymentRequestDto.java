package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateSupplierPaymentRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long supplierId,
    @NotNull LocalDate paymentDate,
    @NotNull PaymentMethod method,
    String reference,
    @NotBlank String currencyCode,
    @NotNull @Positive BigDecimal totalAmount,
    String notes,
    @NotEmpty @Valid List<Allocation> allocations
) {
    public record Allocation(
        @NotNull Long supplierInvoiceId,
        @NotNull @Positive BigDecimal amount
    ) {}
}
