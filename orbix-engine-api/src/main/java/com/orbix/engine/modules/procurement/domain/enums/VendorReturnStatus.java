package com.orbix.engine.modules.procurement.domain.enums;

/** State machine for a vendor return document. DATA-MODEL.md §5.x (Slice H.1). */
public enum VendorReturnStatus {
    DRAFT,
    POSTED,
    CREDITED,
    CANCELLED
}
