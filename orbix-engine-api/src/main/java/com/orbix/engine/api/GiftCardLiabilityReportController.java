package com.orbix.engine.api;

import com.orbix.engine.modules.giftcard.domain.dto.GiftCardLiabilityReportDto;
import com.orbix.engine.modules.giftcard.service.GiftCardLiabilityReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Gift-card outstanding-liability report (F8.5 / US-RPT-013). Sum of
 * {@code current_balance} per (status, currency, branch); the top-level
 * envelope rolls up ACTIVE + FROZEN as outstanding liability per currency.
 *
 * <p>Gated by {@code GIFTCARD.LOOKUP} — the accountant grant — so the
 * finance team can run the report without holding the broader
 * issue / redeem / freeze rights.
 */
@RestController
@RequestMapping("/api/v1/reports/gift-card-liability")
@RequiredArgsConstructor
public class GiftCardLiabilityReportController {

    private final GiftCardLiabilityReportService service;

    @GetMapping
    @PreAuthorize("hasAuthority('GIFTCARD.LOOKUP')")
    public GiftCardLiabilityReportDto report(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant asOf) {
        return service.report(branchId, asOf);
    }
}
