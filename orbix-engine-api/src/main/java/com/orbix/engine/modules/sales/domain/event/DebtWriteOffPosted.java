package com.orbix.engine.modules.sales.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a write-off that has been posted (status = POSTED).
 * Emitted on both the auto-post path (create + amount ≤ threshold + caller has
 * APPROVE perm) and the explicit approve path. Published as
 * {@code "DebtWriteOffPosted.v1"}.
 */
public record DebtWriteOffPosted(
    String uid,
    String targetKind,
    Long targetInvoiceId,
    BigDecimal amount,
    Long requesterUserId,
    Long approverUserId,
    String reason
) {
    public static final String TYPE = "DebtWriteOffPosted.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("targetKind", targetKind);
        map.put("targetInvoiceId", targetInvoiceId);
        map.put("amount", amount);
        map.put("requesterUserId", requesterUserId);
        map.put("approverUserId", approverUserId);
        map.put("reason", reason);
        return map;
    }
}
