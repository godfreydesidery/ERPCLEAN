package com.orbix.engine.modules.sales.domain.enums;

/**
 * Reason a sales document was reprinted. Slice C audit feature — recorded
 * on every {@code SalesInvoiceReprinted.v1} outbox event and the
 * {@code reprint_count}-incrementing service call.
 */
public enum ReprintReason {
    /** Customer wants another copy of the same invoice/receipt. */
    DUPLICATE,
    /** Original was issued to the customer; another physical copy needed (e.g. lost). */
    REISSUE_TO_CUSTOMER,
    /** Internal filing/audit copy (e.g. accounts request). */
    INTERNAL_FILE,
    /** Anything else — must be paired with free-text notes. */
    OTHER
}
