package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.TillReportDto;

/**
 * X / Z reports for till sessions (F5.10 / US-POS-015 / US-POS-016).
 *
 * <p>Both reports recompute from immutable source tables ({@code pos_sale},
 * {@code pos_payment}, {@code cash_pickup}, {@code petty_cash}), so the
 * recompute is deterministic. The X-report is the mid-shift snapshot of an
 * OPEN session (no state change); the Z-report is the post-close
 * recomputation of a CLOSED / RECONCILED session, also returning the
 * variance captured at close.
 *
 * <p>PDF rendering + object-storage upload are deferred to a follow-on slice;
 * this service ships the data-side contract only.
 */
public interface TillReportService {

    /** X-report — mid-shift; the session must be {@code OPEN}. */
    TillReportDto xReport(Long tillSessionId);

    /** Z-report — post-close; the session must be {@code CLOSED} or {@code RECONCILED}. */
    TillReportDto zReport(Long tillSessionId);
}
