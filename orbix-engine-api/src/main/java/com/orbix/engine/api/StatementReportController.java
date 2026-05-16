package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PartyStatementDto;
import com.orbix.engine.modules.sales.service.StatementReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * AR / AP statement reporting (F8.7 / US-RPT-007). Per-party chronological
 * statement with opening balance carried forward, period debits + credits,
 * and closing balance. Default 30-day window when {@code from} / {@code to}
 * are omitted.
 *
 * <p>Not @PreAuthorize-gated — matches the existing sales / procurement
 * controller pattern (authentication still required). Tighten in a
 * follow-up if finance demands a narrower grant.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class StatementReportController {

    private final StatementReportService service;

    @GetMapping("/customer-statement")
    public PartyStatementDto customerStatement(
            @RequestParam Long customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.customerStatement(customerId, from, to);
    }

    @GetMapping("/supplier-statement")
    public PartyStatementDto supplierStatement(
            @RequestParam Long supplierId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.supplierStatement(supplierId, from, to);
    }
}
