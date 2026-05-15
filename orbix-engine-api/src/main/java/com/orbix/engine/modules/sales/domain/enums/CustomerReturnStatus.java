package com.orbix.engine.modules.sales.domain.enums;

/**
 * Customer-return lifecycle (F4.4). DRAFT → POSTED (writes stock moves) →
 * CREDITED (a credit note has been issued) → terminal. DRAFT → CANCELLED.
 */
public enum CustomerReturnStatus {
    DRAFT,
    POSTED,
    CREDITED,
    CANCELLED
}
