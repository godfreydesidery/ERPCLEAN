package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.enums.WastageCategory;

import java.math.BigDecimal;

/**
 * One row of the production-wastage rollup (F8.4 / US-RPT-012). Aggregated
 * per (section, category) over the requested window.
 *
 * <p>{@code totalCost} is best-effort: qty × {@code item.avg_cost} summed
 * across the items in the bucket. The avg cost drifts post-event so the
 * number is approximate — close enough for a daily chef-manager dashboard
 * but not for accounting.
 *
 * <p>{@code distinctItemCount} surfaces breadth of the loss
 * (one item burned vs many), {@code recordCount} surfaces volume of
 * incidents (one big spill vs many small ones).
 */
public record WastageReportRowDto(
    Long sectionId,
    String sectionCode,
    String sectionName,
    Long branchId,
    WastageCategory category,
    BigDecimal totalQty,
    BigDecimal totalCost,
    int distinctItemCount,
    int recordCount
) {}
