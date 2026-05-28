package com.orbix.engine.modules.procurement.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a vendor credit note being issued from a return (Slice H.1).
 * Published as {@code "VendorCreditNoteIssued.v1"}.
 * Downstream subscribers: AP aging, accounting export (Phase 3).
 */
public record VendorCreditNoteIssued(
    String creditNoteUid,
    Long supplierId,
    Long vendorReturnId,
    BigDecimal totalAmount,
    Long issuedByUserId
) {
    public static final String TYPE = "VendorCreditNoteIssued.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("creditNoteUid", creditNoteUid);
        map.put("supplierId", supplierId);
        map.put("vendorReturnId", vendorReturnId);
        map.put("totalAmount", totalAmount);
        map.put("issuedByUserId", issuedByUserId);
        return map;
    }
}
