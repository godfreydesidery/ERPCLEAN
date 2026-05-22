package com.orbix.engine.modules.procurement.domain.enums;

/**
 * LPO lifecycle (F3.1). Flow:
 * DRAFT → PENDING_APPROVAL → APPROVED → PARTIALLY_RECEIVED → RECEIVED.
 * Auto-approval (below threshold) skips PENDING_APPROVAL.
 * DRAFT and PENDING_APPROVAL may be CANCELLED. PARTIALLY_RECEIVED / RECEIVED
 * are flipped by GRN postings (F3.2).
 */
public enum LpoOrderStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}
