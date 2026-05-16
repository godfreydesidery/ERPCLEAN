package com.orbix.engine.modules.day.service;

import java.time.LocalDate;
import java.util.List;

/**
 * EOD gate port (F7.5). Each posting module that holds open work-in-progress
 * implements this and Spring auto-wires the list into
 * {@code BusinessDayServiceImpl.startClosing}. A non-empty return blocks the
 * day from moving OPEN → CLOSING and the operator gets a 422 with every
 * blocker so they can resolve them in one pass.
 *
 * <p>Implementations must be read-only and idempotent — the day service may
 * invoke them more than once during the close sequence.
 */
public interface EodGuard {

    /**
     * @return blockers preventing close of {@code businessDate} for
     *     {@code branchId}; empty list means this module is ready.
     */
    List<EodBlockerDto> check(Long branchId, LocalDate businessDate);

    /** Human-readable module name for log lines and trace context. */
    String moduleName();
}
