package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.dto.BusinessDayOverrideDto;

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
 *
 * <p>Slice D layers the uid handle on top of the composite PK (ADR 0002 —
 * Path A). External entry points take {@code String uid} and resolve to the
 * composite internally; the composite-key methods are kept because POS /
 * sales / procurement / the EOD orchestration all hold
 * {@code (branchId, businessDate)} natively.
 *
 * <p>The {@link com.orbix.engine.modules.day.domain.entity.BusinessDayOverride}
 * aggregate is co-managed here — it is a thin audit record under the day
 * aggregate, not its own root. Overrides are surrogate-Long PK with a uid
 * URL handle and a void-before-post archive lifecycle.
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

    // ---- uid entry points (Slice D / ADR 0002) -----------------------------

    /**
     * External-entry-point read by uid. Tenant predicate enforced via the
     * parent branch's company; cross-tenant lookups throw
     * {@link java.util.NoSuchElementException}.
     */
    BusinessDayDto getBusinessDayByUid(String uid);

    /** uid-keyed variant of {@link #startClosing}. */
    BusinessDayDto startClosingByUid(String uid);

    /** uid-keyed variant of {@link #closeDay}. */
    BusinessDayDto closeDayByUid(String uid, String eodReportObjectKey);

    /** uid-keyed variant of {@link #endDay}. */
    BusinessDayDto endDayByUid(String uid, String eodReportObjectKey);

    /** uid-keyed variant of {@link #previewBlockers}. */
    List<EodBlockerDto> previewBlockersByUid(String uid);

    // ---- business-day overrides --------------------------------------------

    /**
     * Persist a supervisor back-dating override under the day addressed by
     * uid. Emits {@code BusinessDayOverridden.v1} in the same transaction.
     */
    BusinessDayOverrideDto postOverrideByDayUid(String dayUid,
                                                String entityType,
                                                Long entityId,
                                                String reason);

    /**
     * Void an override before its back-dated post has landed. After the
     * post succeeds the override is immutable and re-archive throws.
     * Idempotency: a second archive attempt throws — callers should treat
     * the first response as the source of truth.
     */
    BusinessDayOverrideDto archiveBusinessDayOverrideByUid(String uid);

    List<BusinessDayOverrideDto> listOverrides(Long branchId);
}
