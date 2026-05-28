package com.orbix.engine.modules.common.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Slice F — consolidated dashboard payload. Returned by
 * {@code GET /api/v1/reports/dashboard-rollup}. Replaces the four parallel
 * tile-feed calls (ar-summary + stock-negative + lpo-pending-count +
 * sales-summary + balances-for-stock-alert-count) with a single round-trip.
 *
 * <p>Per-fragment authorisation: the service catches
 * {@link org.springframework.security.access.AccessDeniedException} from
 * each sub-call and serialises that fragment as JSON {@code null}. The
 * dashboard renders {@code null} as "Permission required" — same UX as
 * today's per-tile 403 handling. Inside the sections themselves, individual
 * fields stay {@link BigDecimal} / {@link Long} / {@link Integer} so the
 * dashboard reads them directly with no mapping layer.
 *
 * <p>{@code stockAlertCount} and {@code negativeStockCount} appear in both
 * {@link KpiSection} and {@link AlertSection} because the dashboard renders
 * them as both a KPI tile (numeric count) and an alert row (descriptive).
 * Computed once on the server, serialised twice.
 */
public record DashboardRollupDto(
    Long branchId,
    LocalDate businessDate,
    String currencyCode,
    KpiSection kpi,
    AlertSection alerts
) {

    public record KpiSection(
        BigDecimal todaysSales,
        Integer stockAlerts,
        Integer negativeStockCount,
        Long openInvoices,
        BigDecimal arOutstanding
    ) {}

    public record AlertSection(
        Integer stockAlertCount,
        Integer negativeStockCount,
        Long overdueInvoiceCount,
        Long lposPendingApproval
    ) {}
}
