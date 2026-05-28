package com.orbix.engine.modules.sales.domain.enums;

/**
 * Lifecycle states for a {@link com.orbix.engine.modules.sales.domain.entity.DebtWriteOff}.
 * PENDING_APPROVAL → POSTED (approved) or REJECTED.
 * No separate APPROVED state — approval and post happen in one tx.
 */
public enum DebtWriteOffStatus {
    PENDING_APPROVAL,
    POSTED,
    REJECTED
}
