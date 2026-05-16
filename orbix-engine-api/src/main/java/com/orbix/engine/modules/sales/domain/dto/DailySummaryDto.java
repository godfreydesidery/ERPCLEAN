package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * One-pager daily summary (F8.2 / US-RPT-002). Sales + purchases + cash
 * rollup for a branch on a single business date — what the manager wants on
 * their desk first thing in the morning.
 *
 * <ul>
 *   <li>{@code sales} aggregates POSTED + PARTIALLY_PAID + PAID sales_invoice
 *       totals plus all POSTED pos_sale totals (SALE kind contributes
 *       positively; REFUND kind contributes negatively; VOIDED skipped).</li>
 *   <li>{@code purchases} sums POSTED grn.total_amount for the day.</li>
 *   <li>{@code cash} is keyed by account (CASH_BOX / BANK / MOBILE_MONEY)
 *       with the {@code closing - opening} delta per account.</li>
 * </ul>
 */
public record DailySummaryDto(
    LocalDate businessDate,
    Long branchId,
    SalesBlock sales,
    PurchasesBlock purchases,
    CashBlock cash
) {
    public record SalesBlock(
        BigDecimal invoiceTotal,
        BigDecimal invoiceTax,
        BigDecimal invoiceDiscount,
        int invoiceCount,
        BigDecimal posSaleNet,
        BigDecimal posSaleTax,
        BigDecimal posSaleDiscount,
        int posSaleCount,
        int posRefundCount,
        BigDecimal grandTotal
    ) {}

    public record PurchasesBlock(
        BigDecimal grnTotal,
        BigDecimal grnTax,
        int grnCount
    ) {}

    public record CashBlock(
        BigDecimal openingTotal,
        BigDecimal inTotal,
        BigDecimal outTotal,
        BigDecimal closingTotal,
        Map<CashAccount, BigDecimal> closingByAccount
    ) {}
}
