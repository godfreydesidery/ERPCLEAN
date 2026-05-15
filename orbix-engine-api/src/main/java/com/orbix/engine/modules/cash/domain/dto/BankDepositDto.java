package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.BankDeposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record BankDepositDto(
    Long id,
    Long companyId,
    Long branchId,
    LocalDate businessDate,
    BigDecimal amount,
    String currencyCode,
    String reference,
    String notes,
    Instant at,
    Long postedBy
) {
    public static BankDepositDto from(BankDeposit row) {
        return new BankDepositDto(
            row.getId(), row.getCompanyId(), row.getBranchId(), row.getBusinessDate(),
            row.getAmount(), row.getCurrencyCode(), row.getReference(), row.getNotes(),
            row.getAt(), row.getPostedBy());
    }
}
