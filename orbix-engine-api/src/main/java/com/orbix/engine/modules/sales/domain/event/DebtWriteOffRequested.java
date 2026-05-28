package com.orbix.engine.modules.sales.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a write-off submitted but not yet approved
 * (status = PENDING_APPROVAL). Published as {@code "DebtWriteOffRequested.v1"}.
 */
public record DebtWriteOffRequested(
    String uid,
    String targetKind,
    Long targetInvoiceId,
    BigDecimal amount,
    Long requesterUserId,
    String reason
) {
    public static final String TYPE = "DebtWriteOffRequested.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("targetKind", targetKind);
        map.put("targetInvoiceId", targetInvoiceId);
        map.put("amount", amount);
        map.put("requesterUserId", requesterUserId);
        map.put("reason", reason);
        return map;
    }
}
