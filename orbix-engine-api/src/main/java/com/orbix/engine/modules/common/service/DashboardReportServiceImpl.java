package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.DashboardRollupDto;
import com.orbix.engine.modules.procurement.service.LpoOrderService;
import com.orbix.engine.modules.sales.domain.dto.ArSummaryDto;
import com.orbix.engine.modules.sales.domain.dto.DailySummaryDto;
import com.orbix.engine.modules.sales.service.SalesReportService;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.service.StockMoveService;
import com.orbix.engine.modules.stock.service.StockReportService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * Slice F — per-fragment-authorised rollup. Each sub-call is wrapped in
 * {@link #tryFragment(Supplier)}; an {@link AccessDeniedException} returns
 * {@code null} for that fragment so the response shape survives partial
 * permission grants. The dashboard renders {@code null} as
 * "Permission required" — same UX as today's per-tile 403 handling.
 *
 * <p>This service lives in {@code common.service} because it spans modules
 * and is read-only (no outbox events, no state mutations). ArchUnit's
 * {@link com.orbix.engine.architecture.ModuleBoundaryTest} permits the
 * cross-module reaches via the {@code *Service} interface seam — no named
 * exemption required (the broad {@code ..service..} allowance applies).
 */
@Service
@RequiredArgsConstructor
public class DashboardReportServiceImpl implements DashboardReportService {

    private static final Logger log = LoggerFactory.getLogger(DashboardReportServiceImpl.class);

    // TZS is the Orbix Tanzania default; the company-wide currency setting is
    // not yet a first-class app_setting (see SalesReportServiceImpl#arSummary
    // for the precedent). Frontend re-reads it from the company profile when
    // multi-currency lands.
    private static final String DEFAULT_CURRENCY = "TZS";

    private final SalesReportService salesReportService;
    private final StockReportService stockReportService;
    private final StockMoveService stockMoveService;
    private final LpoOrderService lpoOrderService;

    @Override
    @Transactional(readOnly = true)
    public DashboardRollupDto rollup(Long branchId, LocalDate businessDate) {
        LocalDate date = businessDate != null ? businessDate : LocalDate.now();

        ArSummaryDto ar = tryFragment(() -> salesReportService.arSummary(branchId));
        DailySummaryDto daily = tryFragment(() -> salesReportService.dailySummary(branchId, date));
        List<ItemBranchBalanceDto> negativeBalances = tryFragment(() -> stockReportService.negativeOnHand(branchId));
        Long lposPending = tryFragment(() -> lpoOrderService.countPendingApproval(branchId));

        // Stock-alert count is derived from /balances (existing dashboard
        // pattern: count balances where qtyOnHand <= reorderMin). The
        // listBalances() call is permission-gated on STOCK.COUNT (post-Slice-F
        // pin). Branch is required for that endpoint; when caller is
        // company-wide (branchId null), skip — the dashboard renders the
        // alert tile only when an active branch is selected.
        Integer stockAlertCount = null;
        if (branchId != null) {
            List<ItemBranchBalanceDto> belowReorder = tryFragment(
                () -> stockMoveService.listBalances(branchId, false, true));
            if (belowReorder != null) {
                stockAlertCount = belowReorder.size();
            }
        }

        Integer negativeStockCount = negativeBalances != null ? negativeBalances.size() : null;
        BigDecimal todaysSales = (daily != null && daily.sales() != null) ? daily.sales().grandTotal() : null;
        Long openInvoices = ar != null ? ar.openInvoices() : null;
        BigDecimal arOutstanding = ar != null ? ar.arOutstanding() : null;
        Long overdueInvoiceCount = ar != null ? ar.overdueInvoices() : null;
        String currencyCode = ar != null ? ar.currencyCode() : DEFAULT_CURRENCY;

        DashboardRollupDto.KpiSection kpi = new DashboardRollupDto.KpiSection(
            todaysSales,
            stockAlertCount,
            negativeStockCount,
            openInvoices,
            arOutstanding
        );
        DashboardRollupDto.AlertSection alerts = new DashboardRollupDto.AlertSection(
            stockAlertCount,
            negativeStockCount,
            overdueInvoiceCount,
            lposPending
        );
        return new DashboardRollupDto(branchId, date, currencyCode, kpi, alerts);
    }

    /**
     * Run {@code fragment} and return its result; on
     * {@link AccessDeniedException} return {@code null} (the field is
     * permission-gated and the caller doesn't hold the grant). Any other
     * runtime exception propagates — those are bugs, not 403s.
     */
    private static <T> T tryFragment(Supplier<T> fragment) {
        try {
            return fragment.get();
        } catch (AccessDeniedException ex) {
            log.debug("Dashboard fragment denied: {}", ex.getMessage());
            return null;
        }
    }
}
