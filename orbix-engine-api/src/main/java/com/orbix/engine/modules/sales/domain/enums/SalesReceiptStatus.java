package com.orbix.engine.modules.sales.domain.enums;

/** Sales-receipt lifecycle (F4.3). DRAFT → POSTED → terminal; DRAFT → CANCELLED. */
public enum SalesReceiptStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
