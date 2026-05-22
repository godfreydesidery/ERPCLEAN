package com.orbix.engine.api;

import com.orbix.engine.modules.orders.domain.dto.LaybyAgeingReportDto;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import com.orbix.engine.modules.orders.service.LaybyAgeingReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Layby / pre-order ageing report (F8.6 / US-RPT-014). Open orders
 * bucketed by days-since-create with per-type top-line balances + a
 * flat drill-down sorted oldest-first.
 *
 * <p>Gated by {@code ORDER.READ} — the customer-service desk who chase
 * up stale layby balances.
 */
@RestController
@RequestMapping("/api/v1/reports/layby-ageing")
@RequiredArgsConstructor
public class LaybyAgeingReportController {

    private final LaybyAgeingReportService service;

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER.READ') or hasAuthority('ORDER.MANAGE')")
    public LaybyAgeingReportDto report(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) CustomerOrderType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        return service.report(branchId, type, asOf);
    }
}
