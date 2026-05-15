package com.orbix.engine.modules.procurement.domain.enums;

/**
 * Supplier-invoice lifecycle (F3.3 / F3.4). DRAFT → POSTED → PARTIALLY_PAID → PAID;
 * cancellation is allowed from DRAFT or POSTED (settlement-side transitions are
 * driven by F3.4 supplier payments).
 */
public enum SupplierInvoiceStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    CANCELLED
}
