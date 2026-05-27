package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.CashBook;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response shape for the cash-book projection. Composite-PK aggregate per
 * ADR 0002 (Path A): the four composite components ({@code branchId},
 * {@code account}, {@code currencyCode}, {@code businessDate}) stay on the
 * wire and {@code uid} is the external URL handle. No surrogate {@code id} —
 * the composite is the identity.
 */
public record CashBookDto(
    String uid,
    Long branchId,
    CashAccount account,
    LocalDate businessDate,
    String currencyCode,
    BigDecimal openingAmount,
    BigDecimal inAmount,
    BigDecimal outAmount,
    BigDecimal closingAmount
) {
    public static CashBookDto from(CashBook book) {
        return new CashBookDto(
            book.getUid(),
            book.getBranchId(), book.getAccount(), book.getBusinessDate(),
            book.getCurrencyCode(), book.getOpeningAmount(), book.getInAmount(),
            book.getOutAmount(), book.getClosingAmount());
    }
}
