package com.orbix.engine.modules.giftcard.service;

import com.orbix.engine.modules.giftcard.domain.dto.GiftCardLiabilityReportDto;

import java.time.Instant;

/**
 * Gift-card outstanding-liability rollup (F8.5 / US-RPT-013). Returns the
 * report envelope with top-level per-currency totals (counting only
 * ACTIVE + FROZEN as outstanding) plus a per-(status, currency, branch)
 * drill-down. Optional {@code branchId} scope; optional {@code asOf}
 * cutoff so finance can run the report for a period close.
 */
public interface GiftCardLiabilityReportService {

    GiftCardLiabilityReportDto report(Long branchId, Instant asOf);
}
