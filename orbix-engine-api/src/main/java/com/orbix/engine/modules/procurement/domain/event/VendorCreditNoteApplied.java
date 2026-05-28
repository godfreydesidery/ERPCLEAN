package com.orbix.engine.modules.procurement.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a vendor credit-note application to a supplier invoice (Slice H.1).
 * Published as {@code "VendorCreditNoteApplied.v1"}.
 * Downstream subscribers: AP aging refresh, accounting export (Phase 3).
 */
public record VendorCreditNoteApplied(
    String creditNoteUid,
    String supplierInvoiceUid,
    BigDecimal amount,
    String currencyCode,
    Long allocatedByUserId
) {
    public static final String TYPE = "VendorCreditNoteApplied.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("creditNoteUid", creditNoteUid);
        map.put("supplierInvoiceUid", supplierInvoiceUid);
        map.put("amount", amount);
        map.put("currencyCode", currencyCode);
        map.put("allocatedByUserId", allocatedByUserId);
        return map;
    }
}
