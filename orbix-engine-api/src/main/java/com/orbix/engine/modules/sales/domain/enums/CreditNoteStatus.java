package com.orbix.engine.modules.sales.domain.enums;

/**
 * Customer-credit-note lifecycle (F4.4). POSTED on issue; flips to
 * FULLY_ALLOCATED once {@code allocated_amount == total_amount}. CANCELLED
 * is reserved for accounting reversals — not exposed via the standard API.
 */
public enum CreditNoteStatus {
    POSTED,
    FULLY_ALLOCATED,
    CANCELLED
}
