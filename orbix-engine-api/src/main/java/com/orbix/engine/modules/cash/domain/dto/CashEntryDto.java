package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.CashEntry;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only ledger-row response. Surrogate-Long PK aggregate: carries both
 * {@code id} (stringified on the wire) and {@code uid} (external URL handle).
 * Slice D — no archive / activate lifecycle, immutable wire shape.
 */
public record CashEntryDto(
    String uid,
    Long id,
    Instant at,
    Long companyId,
    Long branchId,
    LocalDate businessDate,
    CashAccount account,
    CashDirection direction,
    /** Functional-currency-converted value. */
    BigDecimal amount,
    /** Value in {@link #currencyCode} (the tender currency). */
    BigDecimal tenderAmount,
    /** Rate used to back-convert {@link #tenderAmount} → {@link #amount}. 1 for functional. */
    BigDecimal fxRateSnapshot,
    /** Tender currency. */
    String currencyCode,
    String refType,
    Long refId,
    GlCategory glCategory,
    String notes,
    Long actorId
) {
    public static CashEntryDto from(CashEntry entry) {
        return new CashEntryDto(
            entry.getUid(),
            entry.getId(), entry.getAt(), entry.getCompanyId(), entry.getBranchId(),
            entry.getBusinessDate(), entry.getAccount(), entry.getDirection(),
            entry.getAmount(), entry.getTenderAmount(), entry.getFxRateSnapshot(),
            entry.getCurrencyCode(), entry.getRefType(), entry.getRefId(),
            entry.getGlCategory(), entry.getNotes(), entry.getActorId());
    }
}
