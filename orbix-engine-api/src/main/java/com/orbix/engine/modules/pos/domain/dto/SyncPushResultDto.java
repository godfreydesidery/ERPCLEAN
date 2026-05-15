package com.orbix.engine.modules.pos.domain.dto;

import java.util.List;

/**
 * Per-item verdicts for a batch push (F5.4). Each result mirrors the request's
 * {@code clientOpId} so the till can ack the outbox row. Accepted items
 * include the server-assigned {@code posSaleId}; rejected items include the
 * error message so the till can flag the row for review.
 */
public record SyncPushResultDto(
    int acceptedCount,
    int rejectedCount,
    List<Item> items
) {
    public record Item(
        String clientOpId,
        boolean accepted,
        Long posSaleId,
        String errorMessage
    ) {
        public static Item accepted(String clientOpId, Long posSaleId) {
            return new Item(clientOpId, true, posSaleId, null);
        }
        public static Item rejected(String clientOpId, String errorMessage) {
            return new Item(clientOpId, false, null, errorMessage);
        }
    }
}
