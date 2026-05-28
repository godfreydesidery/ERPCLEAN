package com.orbix.engine.modules.procurement.domain.enums;

/** State machine for a vendor credit note document. DATA-MODEL.md §5.x (Slice H.1). */
public enum VendorCreditNoteStatus {
    POSTED,
    PARTIALLY_ALLOCATED,
    FULLY_ALLOCATED
}
