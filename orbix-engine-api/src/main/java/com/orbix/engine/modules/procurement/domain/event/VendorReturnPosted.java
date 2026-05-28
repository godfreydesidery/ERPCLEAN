package com.orbix.engine.modules.procurement.domain.event;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Outbox event payload for a vendor return being posted (Slice H.1).
 * Published as {@code "VendorReturnPosted.v1"}.
 * Downstream subscribers: AP aging refresh, stock-on-hand reconciliation (Phase 3).
 */
public record VendorReturnPosted(
    String vendorReturnUid,
    Long supplierId,
    Long branchId,
    BigDecimal totalAmount,
    String moveType,
    Long postedByUserId
) {
    public static final String TYPE = "VendorReturnPosted.v1";

    public Map<String, Object> toPayload() {
        Map<String, Object> map = new HashMap<>();
        map.put("vendorReturnUid", vendorReturnUid);
        map.put("supplierId", supplierId);
        map.put("branchId", branchId);
        map.put("totalAmount", totalAmount);
        map.put("moveType", moveType);
        map.put("postedByUserId", postedByUserId);
        return map;
    }
}
