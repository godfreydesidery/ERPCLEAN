package com.orbix.engine.modules.pos.domain.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response for POST /api/v1/sync/till-session/close.
 * {@code T} inside {@code ApiResponse<T>} — not wrapped manually.
 * Design: docs/design/slice-sync-spine.md §4.
 *
 * <p>On manifest mismatch: {@code status = RECONCILE_INCOMPLETE}, missing/unexpected lists populated.
 * On match: {@code status = CLOSED}, confirmedClientOpIds carries the full op set — client's
 * authority to clear its outbox and flip PosSales.synced = true.
 */
public record TillSessionCloseResultDto(
    String tillSessionUid,
    /** CLOSED | RECONCILE_INCOMPLETE */
    String status,
    BigDecimal openingFloat,
    BigDecimal expectedCash,
    BigDecimal declaredCash,
    BigDecimal variance,
    /** Every clientOpId durably committed server-side for this session. */
    List<String> confirmedClientOpIds,
    /** Populated on RECONCILE_INCOMPLETE: ops the client listed but server never received. */
    List<String> missingClientOpIds,
    /** Populated on RECONCILE_INCOMPLETE: ops server has but client didn't list. */
    List<String> unexpectedClientOpIds,
    /** Z-report storage key; null until EOD generates it. */
    String zReportObjectKey
) {}
