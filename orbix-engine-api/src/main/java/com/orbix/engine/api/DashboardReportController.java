package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.DashboardRollupDto;
import com.orbix.engine.modules.common.service.DashboardReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Slice F — consolidated dashboard rollup. Replaces the four parallel tile
 * feeds (ar-summary + stock-negative + lpo-pending-count + sales-summary +
 * balances-for-stock-alert-count) with a single round-trip.
 *
 * <p>No class-level {@code @PreAuthorize} — authentication is required (the
 * default Spring Security chain), but per-fragment authorisation is handled
 * inside {@link DashboardReportService} (each sub-call wrapped in a try/catch
 * on {@link org.springframework.security.access.AccessDeniedException} so the
 * fragment serialises as {@code null}). The dashboard renders {@code null} as
 * "Permission required".
 *
 * <p>This controller lives separately from
 * {@link SalesAggregateReportController} because its consumer context is the
 * dashboard, not the sales domain — it spans procurement / stock / sales
 * read paths. Lives in {@code com.orbix.engine.api} alongside the other
 * report controllers (flat controller layout per the project convention).
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class DashboardReportController {

    private final DashboardReportService service;

    /**
     * @param branchId optional — null = company-wide (subject to
     *                 {@code BranchScope.requireReadable})
     * @param businessDate optional — defaults to today
     */
    @GetMapping("/dashboard-rollup")
    public DashboardRollupDto rollup(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.rollup(branchId, businessDate);
    }
}
