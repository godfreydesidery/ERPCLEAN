package com.orbix.engine.modules.pos.domain.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Response for GET /api/v1/sync/pull and GET /api/v1/sync/bootstrap.
 * {@code T} inside {@code ApiResponse<T>} — not wrapped manually.
 * Design: docs/design/slice-sync-spine.md §3.2.
 *
 * <p>The cursor is opaque to the client — store and replay verbatim.
 * Internally it encodes {@code {"v":1,"seq":N}} base64.
 * Loop while {@code hasMore=true}, advancing cursor each time.
 */
public record SyncPullResultDto(
    Instant serverTime,
    /** Opaque cursor token; store verbatim, replay on next pull. */
    String nextCursor,
    /** True means call pull again immediately with nextCursor. */
    boolean hasMore,
    /** True means drop caches and call bootstrap (tombstone window exceeded, etc.). */
    boolean resyncRequired,
    /** Per-dataset upsert + delete lists, keyed by dataset name. */
    Map<String, DatasetDto> datasets
) {
    /**
     * One dataset's delta: rows to upsert (raw maps matching the dataset's schema)
     * and string ids to delete from the client's local cache.
     */
    public record DatasetDto(
        List<Object> upserts,
        List<String> deletes
    ) {}
}
