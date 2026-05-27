package com.orbix.engine.api;

import com.orbix.engine.modules.sales.domain.dto.ArSummaryDto;
import com.orbix.engine.modules.sales.service.SalesReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sales aggregate reports under {@code /api/v1/sales/reports}. Distinct from
 * {@link SalesReportController} (which lives under {@code /api/v1/reports}
 * for the daily / Z-history / VAT-return reports) — the {@code sales}-rooted
 * URL is reserved for aggregates that are sales-domain-scoped and need
 * tighter per-feature gating (Slice C: AR-summary tile).
 */
@RestController
@RequestMapping("/api/v1/sales/reports")
@RequiredArgsConstructor
public class SalesAggregateReportController {

    private final SalesReportService service;

    /**
     * Slice C — AR-summary tile feed for the dashboard. Returns the three
     * counts the dashboard reads ({@code arOutstanding},
     * {@code overdueInvoices}, {@code openInvoices}) plus the company
     * currency code so the tile renders without a second call.
     * Permission: {@code SALES.REPORT.AR_SUMMARY}.
     */
    @GetMapping("/ar-summary")
    @PreAuthorize("hasAuthority('SALES.REPORT.AR_SUMMARY')")
    public ArSummaryDto arSummary(@RequestParam(required = false) Long branchId) {
        return service.arSummary(branchId);
    }
}
