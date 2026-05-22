package com.orbix.engine.modules.sales.domain.enums;

/**
 * Sales invoice lifecycle (F4.2). DRAFT → POSTED → settlement via F4.3 sales
 * receipts (PARTIALLY_PAID → PAID). VOIDED is a same-business-day reversal of
 * a POSTED invoice; CANCELLED is for DRAFTs that never posted.
 */
public enum SalesInvoiceStatus {
    DRAFT,
    POSTED,
    PARTIALLY_PAID,
    PAID,
    VOIDED,
    CANCELLED
}
