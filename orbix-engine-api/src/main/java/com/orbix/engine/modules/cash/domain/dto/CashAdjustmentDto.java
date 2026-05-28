package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Supervisor cash-adjustment response. Surrogate-Long PK aggregate: carries
 * both {@code id} (stringified on the wire) and {@code uid} (external URL
 * handle). Slice D — reversal columns ({@code reversedAt},
 * {@code reversedBy}, {@code reversedByEntryId}) are stamped when an
 * adjustment is archived and null while the adjustment is still active.
 */
public record CashAdjustmentDto(
    String uid,
    Long id,
    Long companyId,
    Long branchId,
    LocalDate businessDate,
    CashAccount account,
    CashDirection direction,
    BigDecimal amount,
    String currencyCode,
    String reason,
    Instant at,
    Long postedBy,
    Instant reversedAt,
    Long reversedBy,
    Long reversedByEntryId
) {
    public static CashAdjustmentDto from(CashAdjustment row) {
        return new CashAdjustmentDto(
            row.getUid(),
            row.getId(), row.getCompanyId(), row.getBranchId(), row.getBusinessDate(),
            row.getAccount(), row.getDirection(), row.getAmount(), row.getCurrencyCode(),
            row.getReason(), row.getAt(), row.getPostedBy(),
            row.getReversedAt(), row.getReversedBy(), row.getReversedByEntryId());
    }
}
