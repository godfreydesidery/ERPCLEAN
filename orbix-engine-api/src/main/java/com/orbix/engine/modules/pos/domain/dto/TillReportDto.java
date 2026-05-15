package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.enums.PettyCashCategory;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Snapshot of a till session for reporting (F5.10 / US-POS-015 / US-POS-016).
 * Same shape for X-reports (mid-shift, computed live) and Z-reports (post-close,
 * recomputed from the immutable source tables). The {@code reportType} field
 * tells callers which one they got.
 *
 * <p>All monetary fields are in the company's functional currency. FX-tendered
 * payments are reported using their snapped functional-currency amount.
 *
 * <p>Designed so the Flutter POS can print the X-report receipt and the close
 * receipt directly from this DTO; a follow-on slice will produce a PDF and
 * upload it to object storage on Z.
 */
public record TillReportDto(
    ReportType reportType,
    Long tillSessionId,
    Long tillId,
    Long branchId,
    LocalDate businessDate,
    TillSessionStatus status,
    Long cashierId,
    Long supervisorId,
    Instant openedAt,
    Instant closedAt,
    Instant generatedAt,

    /** Number of POSTED + SALE rows on the session. */
    int salesCount,
    /** Number of POSTED + REFUND rows on the session. */
    int refundsCount,
    /** Number of VOIDED rows (cash returned to customer, doesn't move the drawer). */
    int voidsCount,

    /** Σ of POSTED-SALE {@code total_amount} (functional). */
    BigDecimal grossSales,
    /** Σ of POSTED-REFUND {@code total_amount} (functional). */
    BigDecimal grossRefunds,
    /** {@code grossSales − grossRefunds}. */
    BigDecimal netSales,

    /** Σ of POSTED-SALE {@code discount_amount} (functional). */
    BigDecimal discountTotal,
    /** Σ of POSTED-SALE {@code tax_amount} (functional). */
    BigDecimal taxTotal,

    /** Per-method breakdown of POSTED-SALE tender (functional). Methods with zero are omitted. */
    Map<PosPaymentMethod, BigDecimal> tenderByMethod,
    /** Per-method breakdown of POSTED-REFUND tender (functional). */
    Map<PosPaymentMethod, BigDecimal> refundByMethod,

    /** Σ pickups (TILL → CASH_BOX) on this session. */
    BigDecimal cashPickupTotal,
    /** Σ petty cash payouts on this session. */
    BigDecimal pettyCashTotal,
    /** Per-category petty cash breakdown. */
    Map<PettyCashCategory, BigDecimal> pettyCashByCategory,

    /** Opening float captured at session open. */
    BigDecimal openingFloat,
    /** Computed expected drawer cash (see {@link com.orbix.engine.modules.pos.service.TillSessionService}). */
    BigDecimal expectedCash,
    /** Declared on close. {@code null} until the session is closed. */
    BigDecimal declaredCash,
    /** {@code declared − expected}. {@code null} until close. */
    BigDecimal variance,

    List<LineRef> sales,
    List<LineRef> refunds,
    List<LineRef> voids
) {
    public enum ReportType { X, Z }

    /** Minimal per-row reference so the receipt can list sale numbers + totals. */
    public record LineRef(Long posSaleId, String number, BigDecimal totalAmount, Instant saleAt) {}
}
