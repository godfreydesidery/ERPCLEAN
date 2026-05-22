package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business-day lifecycle (F2.1 + F7.5). State machine OPEN -> CLOSING ->
 * CLOSED. Invariants: at most one non-closed day per branch; business dates
 * are monotonic (no opening a day on or before the latest existing day).
 *
 * <p>F7.5 adds the {@link EodGuard} pre-flight check on
 * {@link #startClosing} (POS / procurement / production all gate the
 * close), auto-roll on successful {@link #closeDay} (creates the next
 * day's OPEN row with {@code opened_by = SYSTEM}), and the
 * {@link #endDay} convenience that runs startClosing + closeDay in one
 * idempotent call.
 */
public interface BusinessDayService {

    /** The branch's current non-closed day (OPEN or CLOSING), if any. */
    Optional<BusinessDayDto> getCurrentDay(Long branchId);

    List<BusinessDayDto> listDays(Long branchId);

    BusinessDayDto openDay(Long branchId, LocalDate businessDate);

    /**
     * Read-only EOD blocker preview — runs every {@link EodGuard} and returns
     * the aggregated list without mutating state. Operator-friendly endpoint
     * for "what's stopping me from closing the day?" before they call
     * {@link #endDay}.
     */
    List<EodBlockerDto> previewBlockers(Long branchId, LocalDate businessDate);

    /** OPEN -> CLOSING. Throws {@link EodBlockedException} if any guard reports blockers. */
    BusinessDayDto startClosing(Long branchId, LocalDate businessDate);

    /**
     * CLOSING -> CLOSED + auto-roll: a fresh OPEN row for
     * {@code businessDate + 1} is created with {@code opened_by = SYSTEM}.
     */
    BusinessDayDto closeDay(Long branchId, LocalDate businessDate, String eodReportObjectKey);

    /**
     * End-of-day orchestration (TC-DAY-006 / TC-DAY-025): runs startClosing
     * + closeDay in one call. Idempotent — already-CLOSED days return the
     * stored row without re-emitting events.
     */
    BusinessDayDto endDay(Long branchId, LocalDate businessDate, String eodReportObjectKey);
}
