package com.orbix.engine.modules.sales.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Slice G — AR aging report. Per-customer rollup of open invoices into
 * 5 buckets ({@code CURRENT} + 4 overdue bands matching US-DEBT-003).
 *
 * <p>Long-id fields stringify globally via {@code IdLongAsStringSerializerModifier};
 * decimals stay numeric. Sorted by {@code oldestDaysOverdue} desc.
 */
public record DebtAgingDto(
    LocalDate asOf,
    Long branchId,
    String currencyCode,
    Totals totals,
    List<CustomerRow> rows
) {

    public record Totals(
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        long customerCount
    ) {}

    public record CustomerRow(
        Long customerId,
        String customerUid,
        String customerName,
        BigDecimal current,
        BigDecimal d1_30,
        BigDecimal d31_60,
        BigDecimal d61_90,
        BigDecimal d90_plus,
        BigDecimal totalOutstanding,
        Integer oldestDaysOverdue,
        BigDecimal creditLimit,
        BigDecimal creditUtilisation
    ) {}
}
