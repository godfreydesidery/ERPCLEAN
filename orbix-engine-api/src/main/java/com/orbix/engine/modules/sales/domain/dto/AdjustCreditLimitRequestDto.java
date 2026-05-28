package com.orbix.engine.modules.sales.domain.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Slice G — request body for the debt-surface credit-limit adjust endpoint.
 * Reason is required for audit traceability.
 */
public record AdjustCreditLimitRequestDto(
    @NotNull @PositiveOrZero @Digits(integer = 14, fraction = 4) BigDecimal newLimit,
    @Size(max = 500) String reason
) {}
