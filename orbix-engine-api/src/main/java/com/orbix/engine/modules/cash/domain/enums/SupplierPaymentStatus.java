package com.orbix.engine.modules.cash.domain.enums;

/** Supplier-payment lifecycle (F3.4). DRAFT → POSTED → terminal; DRAFT → CANCELLED. */
public enum SupplierPaymentStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
