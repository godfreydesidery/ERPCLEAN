package com.orbix.engine.modules.pos.domain.dto;

import java.time.Instant;
import java.util.List;

/**
 * Per-op verdicts for a batch push (US-POS-018).
 * {@code T} inside {@code ApiResponse<T>} — not wrapped manually.
 * Design: docs/design/slice-sync-spine.md §2.2.
 */
public record SyncPushResultDto(
    int batchAcceptedCount,
    int batchRejectedCount,
    /** Server-authoritative receipt timestamp — client uses for clock-skew estimation. */
    Instant serverReceivedAt,
    /** True when the server tells the client to drop caches and call /bootstrap. */
    boolean resyncRequired,
    List<OpResultDto> results
) {
    /**
     * Verdict for one op in the batch.
     *
     * <p>Verdicts:
     * <ul>
     *   <li><b>ACCEPTED</b> — newly applied. Client marks outbox row SENT.</li>
     *   <li><b>DUPLICATE</b> — clientOpId already applied (retry after lost response).
     *       Server returns original row ids — functionally identical to ACCEPTED.</li>
     *   <li><b>REJECTED</b> — permanent business failure. Client marks row NEEDS_REVIEW.</li>
     *   <li><b>DEFERRED</b> — dependsOn op not yet ACCEPTED/DUPLICATE. Client re-pushes next cycle.</li>
     * </ul>
     */
    public record OpResultDto(
        String clientOpId,
        /** ACCEPTED | DUPLICATE | REJECTED | DEFERRED */
        String verdict,
        /** id of the created/affected server row (JSON:API string per global IdLongAsStringSerializerModifier). */
        String serverEntityId,
        /** uid of the affected row for navigation. */
        String serverEntityUid,
        /** Server-authoritative document number. */
        String serverNumber,
        String errorCode,
        String errorMessage
    ) {
        public static OpResultDto accepted(String clientOpId, Long id, String uid, String number) {
            return new OpResultDto(clientOpId, "ACCEPTED", id == null ? null : String.valueOf(id),
                uid, number, null, null);
        }

        public static OpResultDto duplicate(String clientOpId, Long id, String uid, String number) {
            return new OpResultDto(clientOpId, "DUPLICATE", id == null ? null : String.valueOf(id),
                uid, number, null, null);
        }

        public static OpResultDto rejected(String clientOpId, String errorCode, String errorMessage) {
            return new OpResultDto(clientOpId, "REJECTED", null, null, null, errorCode, errorMessage);
        }

        public static OpResultDto deferred(String clientOpId) {
            return new OpResultDto(clientOpId, "DEFERRED", null, null, null,
                "DEFERRED", "dependsOn op not yet applied; re-push next cycle");
        }
    }
}
