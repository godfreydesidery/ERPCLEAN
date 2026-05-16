package com.orbix.engine.modules.common.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Customer or supplier statement (F8.7 / US-RPT-007). Sliced into:
 * opening balance (computed from all activity strictly before {@code from}),
 * chronological window entries, and closing balance (opening + period
 * debits − period credits — also equals the last entry's running balance).
 *
 * <p>Same shape for both AR and AP — direction reverses (customer "debit"
 * is what they owe us; supplier "debit" is what we owe them).
 */
public record PartyStatementDto(
    Long partyId,
    String partyType,
    LocalDate from,
    LocalDate to,
    BigDecimal openingBalance,
    BigDecimal periodDebits,
    BigDecimal periodCredits,
    BigDecimal closingBalance,
    List<StatementEntryDto> entries
) {}
