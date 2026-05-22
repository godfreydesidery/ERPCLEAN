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

public record CreateLpoOrderRequestDto(
    @NotBlank String number,
    @NotNull Long branchId,
    @NotNull Long supplierId,
    @NotNull LocalDate orderDate,
    LocalDate expectedDeliveryDate,
    @NotBlank String currencyCode,
    String notes,
    @NotEmpty @Valid List<Line> lines
) {
    public record Line(
        @NotNull Long itemId,
        Long uomId,
        @NotNull @Positive BigDecimal orderedQty,
        @NotNull @PositiveOrZero BigDecimal unitPrice,
        Long vatGroupId,
        BigDecimal discountPct
    ) {}
}
