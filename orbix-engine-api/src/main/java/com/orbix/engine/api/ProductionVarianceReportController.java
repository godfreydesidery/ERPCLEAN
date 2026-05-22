package com.orbix.engine.api;

import com.orbix.engine.modules.production.domain.dto.ProductionVarianceRowDto;
import com.orbix.engine.modules.production.service.ProductionVarianceReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Production variance report (F7.4 / US-PROD-008 / TC-PROD-021). Per-batch
 * planned vs actual + wastage breakdown. All filters optional.
 */
@RestController
@RequestMapping("/api/v1/reports/production-variance")
@RequiredArgsConstructor
public class ProductionVarianceReportController {

    private final ProductionVarianceReportService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.READ_REPORT') or hasAuthority('PROD.MANAGE_BATCH')")
    public List<ProductionVarianceRowDto> report(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) Long bomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.report(branchId, sectionId, bomId, from, to);
    }
}
