package com.orbix.engine.modules.procurement.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Slice G.1 — AP aging report. Per-supplier rollup of open invoices into
 * 5 buckets (CURRENT + 4 overdue bands, matching US-DEBT-003 on the AP side).
 *
 * <p>Long-id fields stringify globally via {@code IdLongAsStringSerializerModifier};
 * decimals stay numeric. Sorted by {@code oldestDaysOverdue} desc.
 */
public record SupplierAgingDto(
    LocalDate asOf,
    Long branchId,
    String currencyCode,
    Totals totals,
    List<SupplierRow> rows
) {

    public record Totals(
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        long supplierCount
    ) {}

    public record SupplierRow(
        Long supplierId,
        String supplierUid,
        String supplierName,
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        Integer oldestDaysOverdue
    ) {}
}
