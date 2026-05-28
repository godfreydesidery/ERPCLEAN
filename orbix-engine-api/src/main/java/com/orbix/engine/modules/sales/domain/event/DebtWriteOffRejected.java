package com.orbix.engine.modules.sales.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a write-off that has been rejected (status = REJECTED).
 * Published as {@code "DebtWriteOffRejected.v1"}.
 */
public record DebtWriteOffRejected(
    String uid,
    String targetKind,
    Long targetInvoiceId,
    BigDecimal amount,
    Long requesterUserId,
    Long approverUserId,
    String reasonForReject
) {
    public static final String TYPE = "DebtWriteOffRejected.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("targetKind", targetKind);
        map.put("targetInvoiceId", targetInvoiceId);
        map.put("amount", amount);
        map.put("requesterUserId", requesterUserId);
        map.put("approverUserId", approverUserId);
        map.put("reasonForReject", reasonForReject);
        return map;
    }
}
