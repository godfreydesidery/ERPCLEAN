package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.CashBook;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CashBookDto(
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
            book.getBranchId(), book.getAccount(), book.getBusinessDate(),
            book.getCurrencyCode(), book.getOpeningAmount(), book.getInAmount(),
            book.getOutAmount(), book.getClosingAmount());
    }
}
