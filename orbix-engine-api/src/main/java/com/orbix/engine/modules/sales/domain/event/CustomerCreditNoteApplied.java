package com.orbix.engine.modules.sales.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a credit-note application to an invoice (Slice H).
 * Published as {@code "CustomerCreditNoteApplied.v1"}.
 * Downstream subscribers: AR aging refresh, accounting export (Phase 3).
 */
public record CustomerCreditNoteApplied(
    String creditNoteUid,
    String salesInvoiceUid,
    BigDecimal amount,
    String currencyCode,
    Long allocatedByUserId
) {
    public static final String TYPE = "CustomerCreditNoteApplied.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("creditNoteUid", creditNoteUid);
        map.put("salesInvoiceUid", salesInvoiceUid);
        map.put("amount", amount);
        map.put("currencyCode", currencyCode);
        map.put("allocatedByUserId", allocatedByUserId);
        return map;
    }
}
