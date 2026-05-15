package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Supervisor adjustment payload (F6.3 / TC-CASH-013). Reason is mandatory
 * and audited. Caller must hold {@code CASH.ADJUST}.
 */
public record PostCashAdjustmentRequestDto(
    @NotNull Long branchId,
    @NotNull CashAccount account,
    @NotNull CashDirection direction,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = 2000) String reason
) {}
