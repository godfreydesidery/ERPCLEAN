package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPullResultDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionCloseRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionCloseResultDto;

/**
 * Offline-sync surface for tills (US-POS-017 / US-POS-018).
 * Design: docs/design/slice-sync-spine.md.
 *
 * <p>The contract version header is validated by the controller before calling
 * these methods; the service operates on an already-validated request.
 */
public interface SyncService {

    /**
     * Push a heterogeneous batch of device-outbox operations.
     * Each op is committed in its own REQUIRED tx — one REJECTED op never
     * rolls back siblings. Idempotency guaranteed by per-table
     * {@code uk_*_client_op (company_id, client_op_id)} constraints.
     * Not {@code @Transactional} at the batch level by design.
     */
    SyncPushResultDto pushBatch(SyncPushRequestDto request);

    /**
     * Pull reference-data deltas since {@code cursorToken}.
     * {@code datasets} is a comma-separated list of dataset names
     * ({@code catalog, price, customer, balance, route}); null/blank = all.
     * Returns paged results with a monotonic {@code change_seq} cursor.
     */
    SyncPullResultDto pull(String cursorToken, String datasets);

    /**
     * Full snapshot for a fresh or reinstalled device.
     * Equivalent to pull from the zero cursor with no page cap.
     * {@code datasets} same semantics as {@link #pull}.
     */
    SyncPullResultDto bootstrap(String datasets);

    /**
     * Reconciliation handshake for till-session close (design §4).
     * Validates the client manifest, closes the session on match, returns
     * RECONCILE_INCOMPLETE on mismatch so the client can re-push missing ops.
     */
    TillSessionCloseResultDto closeTillSession(TillSessionCloseRequestDto request);

    // ---- Legacy snapshot endpoints (kept for backward-compat; superseded by pull/bootstrap) ----

    /** Active items + barcodes + price-list prices + per-branch on-hand qty. */
    CatalogSnapshotDto catalogSnapshot(Long branchId, Long priceListId);

    /** Per-branch current on-hand for every item that has a balance row. */
    BalanceSnapshotDto balanceSnapshot(Long branchId);
}
