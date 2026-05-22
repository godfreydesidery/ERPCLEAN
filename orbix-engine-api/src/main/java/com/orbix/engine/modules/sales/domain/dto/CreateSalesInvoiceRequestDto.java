package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateSalesInvoiceRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long customerId,
    Long salesAgentId,
    @NotNull LocalDate invoiceDate,
    LocalDate dueDate,
    @NotNull PaymentTerms paymentTerms,
    @NotBlank String currencyCode,
    @NotNull Long priceListId,
    /** Optional discount-approver — required when any line's discountPct exceeds the threshold. */
    Long discountApproverId,
    String reference,
    String notes,
    @NotEmpty @Valid List<Line> lines
) {
    public record Line(
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal qty,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        BigDecimal discountPct,
        Long vatGroupId
    ) {}
}
