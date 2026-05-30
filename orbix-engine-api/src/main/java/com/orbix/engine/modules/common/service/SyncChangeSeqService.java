package com.orbix.engine.modules.common.service;

/**
 * Vends the next value from the shared {@code sync_change_seq} DB sequence.
 *
 * <p>Every service that writes a sync-exposed reference row (Item,
 * PriceListItem, and any future table listed in the pull endpoint) must call
 * {@link #next()} and stamp the returned value into the row's {@code
 * change_seq} column before saving, so {@code /sync/pull?cursor=N} returns
 * the delta.
 *
 * <p>The sequence is shared across all synced tables intentionally (see
 * docs/design/slice-sync-spine.md §3.3): one cursor scalar covers all
 * datasets in v1. A per-dataset cursor requires no client change (the token
 * is opaque) and can be added later without breaking the contract.
 */
public interface SyncChangeSeqService {

    /**
     * Returns the next monotonically increasing value from
     * {@code sync_change_seq}. Each call allocates a new value; never returns
     * the same value twice. Callers must be inside an active transaction so
     * the sequence allocation and the row write are visible atomically.
     */
    long next();
}
