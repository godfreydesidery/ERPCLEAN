package com.orbix.engine.api;

import com.orbix.engine.modules.production.domain.dto.WastageReportRowDto;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import com.orbix.engine.modules.production.service.WastageReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Production wastage rollup (F8.4 / US-RPT-012). Per-(section, category)
 * totals over a window — the chef-manager's daily dashboard view.
 * Complements the per-batch variance side at
 * {@code /api/v1/reports/production-variance} (F7.4).
 *
 * <p>The wastage list endpoint at {@code /api/v1/production-wastage}
 * (F7.3c) already exposes the per-batch drill-down — this controller is
 * the rollup view.
 */
@RestController
@RequestMapping("/api/v1/reports/production-wastage")
@RequiredArgsConstructor
public class ProductionWastageReportController {

    private final WastageReportService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.READ_REPORT') or hasAuthority('PROD.RECORD_WASTAGE')")
    public List<WastageReportRowDto> report(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) WastageCategory category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(branchId, sectionId, category, from, to);
    }
}
