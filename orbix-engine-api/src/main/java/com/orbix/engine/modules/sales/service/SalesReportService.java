package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.sales.domain.dto.ArSummaryDto;
import com.orbix.engine.modules.sales.domain.dto.DailySalesRowDto;
import com.orbix.engine.modules.sales.domain.dto.DailySummaryDto;
import com.orbix.engine.modules.sales.domain.dto.ZHistoryEntryDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Sales reporting (F8.2). Three reports for the manager / accountant:
 *
 * <ul>
 *   <li>{@code dailySales} (US-RPT-001) — flat per-document list for a branch
 *       on a date, blending sales_invoice + pos_sale rows so a credit
 *       invoice and a cash POS receipt sit side-by-side.</li>
 *   <li>{@code dailySummary} (US-RPT-002) — sales + purchases + cash one-pager
 *       rollup for the manager's desk.</li>
 *   <li>{@code zHistory} (US-RPT-003) — every till session whose
 *       business_date falls in the range, each with its full
 *       {@code TillReportDto} so the auditor can compare across a window.
 *       OPEN sessions are skipped (no Z-report yet computable).</li>
 * </ul>
 */
public interface SalesReportService {

    List<DailySalesRowDto> dailySales(Long branchId, LocalDate businessDate);

    DailySummaryDto dailySummary(Long branchId, LocalDate businessDate);

    List<ZHistoryEntryDto> zHistory(Long branchId, LocalDate from, LocalDate to);

    /**
     * Slice C — AR-summary tile feed for the dashboard. {@code branchId}
     * is optional: null = company-wide aggregate (subject to
     * {@code BranchScope.requireReadable}).
     */
    ArSummaryDto arSummary(Long branchId);
}
