package com.orbix.engine.modules.sales.domain.dto;

import java.math.BigDecimal;

/**
 * AR-summary dashboard tile payload (Slice C GAP 2.C + 8.A).
 *
 * <p>{@code arOutstanding} is the sum of {@code (total_amount - paid_amount)}
 * on POSTED + PARTIALLY_PAID invoices for the requested branch (null
 * branch = company-wide).
 *
 * <p>{@code overdueInvoices} counts POSTED + PARTIALLY_PAID invoices whose
 * {@code due_date < today} and which still carry an outstanding balance.
 *
 * <p>{@code openInvoices} counts POSTED + PARTIALLY_PAID invoices with any
 * outstanding balance (regardless of due date).
 *
 * <p>Field names match the Angular dashboard signals
 * ({@code openInvoices}/{@code arOutstanding}/{@code overdueInvoices}) so
 * no mapping layer is needed on the frontend.
 */
public record ArSummaryDto(
    BigDecimal arOutstanding,
    long overdueInvoices,
    long openInvoices,
    String currencyCode
) {}
