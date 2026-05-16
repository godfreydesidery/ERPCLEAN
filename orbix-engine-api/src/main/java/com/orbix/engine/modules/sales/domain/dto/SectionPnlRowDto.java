package com.orbix.engine.modules.sales.domain.dto;

import java.math.BigDecimal;

/**
 * One row of the section P&L report (F8.3 / US-RPT-011). Per-section
 * revenue minus COGS for a (branch, period). POS-only at MVP — back-office
 * {@code sales_invoice} doesn't carry a section dimension (per
 * DATA-MODEL.md §2129).
 *
 * <p>{@code wastageQty} / {@code wastageCost} fold in production wastage
 * for the section so the kitchen / bakery P&L surfaces the spoilage drag.
 * {@code wastageCost} is best-effort (qty × current item.avg_cost) — it
 * drifts from the actual cost-at-event but is close enough for a daily
 * dashboard.
 */
public record SectionPnlRowDto(
    Long sectionId,
    String sectionCode,
    String sectionName,
    Long branchId,
    BigDecimal revenue,
    BigDecimal refunds,
    BigDecimal cogs,
    BigDecimal grossMargin,
    BigDecimal wastageQty,
    BigDecimal wastageCost,
    int saleCount,
    int refundCount
) {}
