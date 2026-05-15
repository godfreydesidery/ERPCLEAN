package com.orbix.engine.modules.sales.domain.enums;

/** Packing-list lifecycle (F4.5). DRAFT → DISPATCHED → DELIVERED → terminal; DRAFT → CANCELLED. */
public enum PackingListStatus {
    DRAFT,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
