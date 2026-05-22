package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;

/**
 * Offline-sync surface for tills (F5.4). The Flutter POS keeps a local outbox
 * of POS sales it could not push (network down, server unreachable) plus a
 * catalog snapshot it can scan against while disconnected. When the network
 * returns the till calls {@link #pushBatch}; each item is processed
 * independently so a single failure doesn't drop the rest.
 */
public interface SyncService {

    /**
     * Push a batch of locally-committed POS sales. Each item is committed in
     * its own transaction; idempotency is guaranteed by the underlying
     * {@code client_op_id} unique constraint per company.
     */
    SyncPushResultDto pushBatch(SyncPushRequestDto request);

    /** Active items + barcodes + price-list prices + per-branch on-hand qty. */
    CatalogSnapshotDto catalogSnapshot(Long branchId, Long priceListId);

    /** Per-branch current on-hand for every item that has a balance row. */
    BalanceSnapshotDto balanceSnapshot(Long branchId);
}
