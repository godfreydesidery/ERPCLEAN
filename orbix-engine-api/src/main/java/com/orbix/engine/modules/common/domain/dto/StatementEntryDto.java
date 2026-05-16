package com.orbix.engine.modules.common.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One line of an AR / AP statement (F8.7 / US-RPT-007). Chronological
 * timeline entry — invoice, receipt, payment, credit note. Either
 * {@code debit} or {@code credit} is non-zero per row (debits build AR/AP,
 * credits reduce). {@code balance} is the rolling running total after this
 * entry is applied.
 *
 * <p>{@code kind} is a string rather than an enum because the value set
 * differs between AR (INVOICE / RECEIPT / CREDIT_NOTE) and AP
 * (INVOICE / PAYMENT) — the controller picks the right string.
 *
 * <p>{@code voided} marks an entry that was reversed (VOIDED status on
 * sales invoice etc.). The row is included for traceability but contributes
 * zero to debit / credit / balance.
 */
public record StatementEntryDto(
    LocalDate date,
    String kind,
    Long refId,
    String number,
    String reference,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balance,
    boolean voided
) {}
