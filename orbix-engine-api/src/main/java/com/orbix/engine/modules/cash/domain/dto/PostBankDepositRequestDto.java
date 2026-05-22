package com.orbix.engine.modules.cash.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * End-of-day banking deposit slip (F6.3 / TC-CASH-012). Posts paired entries
 * OUT-CASH_BOX + IN-BANK with {@code ref_type = BankDeposit} and the
 * deposit row's id as {@code ref_id}. Caller must hold {@code CASH.BANKING}.
 */
public record PostBankDepositRequestDto(
    @NotNull Long branchId,
    @NotNull @Positive BigDecimal amount,
    @NotBlank @Size(max = 80) String reference,
    @Size(max = 2000) String notes
) {}
