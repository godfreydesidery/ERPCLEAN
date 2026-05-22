package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.VatReturnDto;
import com.orbix.engine.modules.sales.domain.dto.DailySalesRowDto;
import com.orbix.engine.modules.sales.domain.dto.DailySummaryDto;
import com.orbix.engine.modules.sales.domain.dto.SectionPnlRowDto;
import com.orbix.engine.modules.sales.domain.dto.ZHistoryEntryDto;
import com.orbix.engine.modules.sales.service.SalesReportService;
import com.orbix.engine.modules.sales.service.SectionPnlReportService;
import com.orbix.engine.modules.sales.service.VatReturnReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Sales-side reporting (F8.2 / US-RPT-001..003). Matches the existing
 * {@code SalesInvoiceController} read pattern — not @PreAuthorize-gated
 * (authentication still required), since sales reports are inspected by
 * every manager / accountant. Refine the gating in a follow-up if a
 * tighter grant is required.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class SalesReportController {

    private final SalesReportService service;
    private final SectionPnlReportService sectionPnlService;
    private final VatReturnReportService vatReturnService;

    /** US-RPT-001 — flat per-document list blending sales_invoice + pos_sale. */
    @GetMapping("/sales-daily")
    public List<DailySalesRowDto> dailySales(
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.dailySales(branchId, businessDate);
    }

    /** US-RPT-002 — sales + purchases + cash one-pager rollup. */
    @GetMapping("/sales-summary")
    public DailySummaryDto dailySummary(
            @RequestParam(required = false) Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate businessDate) {
        return service.dailySummary(branchId, businessDate);
    }

    /**
     * US-RPT-003 — every till session in {@code [from, to]} with its
     * {@code TillReportDto}. OPEN sessions skipped (Z-report not yet
     * computable). Defaults to the last 7 days when {@code from} / {@code to}
     * are omitted.
     */
    @GetMapping("/z-history")
    public List<ZHistoryEntryDto> zHistory(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.zHistory(branchId, from, to);
    }

    /**
     * US-RPT-011 — per-section revenue minus COGS minus wastage cost over
     * a {@code [from, to]} window. POS-only at MVP (back-office
     * {@code sales_invoice} doesn't carry a section dimension). Defaults to
     * the last 30 days when {@code from} / {@code to} are omitted.
     */
    @GetMapping("/section-pnl")
    public List<SectionPnlRowDto> sectionPnl(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return sectionPnlService.report(branchId, from, to);
    }

    /**
     * US-NFR-COMP-001 — per-VAT-group output + input rollup for a period,
     * plus grand totals for the tax filing. Defaults to the previous full
     * calendar month when {@code from} / {@code to} are omitted.
     */
    @GetMapping("/vat-return")
    public VatReturnDto vatReturn(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return vatReturnService.vatReturn(branchId, from, to);
    }
}
