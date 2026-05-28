package com.orbix.engine.modules.procurement.domain.enums;

/**
 * Why goods are being returned to a supplier.
 * Parallel to {@code sales.domain.enums.ReturnReason} — kept separate because
 * {@code BUYER_REMORSE} is AR-only and procurement imports from sales would
 * violate {@code ModuleBoundaryTest}. DATA-MODEL.md §5.x (Slice H.1).
 */
public enum VendorReturnReason {
    DAMAGED,
    WRONG_ITEM,
    EXPIRED,
    OTHER
}
