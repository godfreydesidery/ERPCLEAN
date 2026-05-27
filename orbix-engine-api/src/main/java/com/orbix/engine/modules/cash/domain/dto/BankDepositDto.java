package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.BankDeposit;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * EOD bank-deposit response. Surrogate-Long PK aggregate: carries both
 * {@code id} (stringified on the wire) and {@code uid} (external URL
 * handle). Slice D — reversal columns ({@code reversedAt},
 * {@code reversedBy}, {@code reversedByOutEntryId},
 * {@code reversedByInEntryId}) are stamped when a deposit is archived; the
 * two compensating-entry ids let consumers link directly to the reversing
 * ledger rows.
 */
public record BankDepositDto(
    String uid,
    Long id,
    Long companyId,
    Long branchId,
    LocalDate businessDate,
    BigDecimal amount,
    String currencyCode,
    String reference,
    String notes,
    Instant at,
    Long postedBy,
    Instant reversedAt,
    Long reversedBy,
    Long reversedByOutEntryId,
    Long reversedByInEntryId
) {
    public static BankDepositDto from(BankDeposit row) {
        return new BankDepositDto(
            row.getUid(),
            row.getId(), row.getCompanyId(), row.getBranchId(), row.getBusinessDate(),
            row.getAmount(), row.getCurrencyCode(), row.getReference(), row.getNotes(),
            row.getAt(), row.getPostedBy(),
            row.getReversedAt(), row.getReversedBy(),
            row.getReversedByOutEntryId(), row.getReversedByInEntryId());
    }
}
