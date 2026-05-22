package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.enums.ReturnReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateCustomerReturnRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long customerId,
    Long originalInvoiceId,
    @NotNull LocalDate returnDate,
    @NotNull ReturnReason reason,
    boolean restock,
    String notes,
    @NotEmpty @Valid List<Line> lines
) {
    public record Line(
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal returnedQty,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        Long vatGroupId,
        Long originalLineId
    ) {}
}
