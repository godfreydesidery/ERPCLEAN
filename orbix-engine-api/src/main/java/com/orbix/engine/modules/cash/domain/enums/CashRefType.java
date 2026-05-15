package com.orbix.engine.modules.cash.domain.enums;

/**
 * Canonical {@code cash_entry.ref_type} values. Stored on the row as a
 * plain string (enum-on-the-wire) so additions are additive.
 */
public final class CashRefType {
    private CashRefType() {}

    /** Opening float on till session open — paired IN-TILL / OUT-CASH_BOX. */
    public static final String TILL_FLOAT = "TillFloat";
    /** Closing cash transfer + variance on till session close. */
    public static final String TILL_SESSION_CLOSE = "TillSessionClose";
    public static final String TILL_VARIANCE = "TillVariance";

    /** POS cash tender — one row per {@code pos_payment} of method {@code CASH}. */
    public static final String POS_SALE_PAYMENT = "PosSalePayment";
    /** POS refund cash tender — same, for refund-kind sales. */
    public static final String POS_REFUND_PAYMENT = "PosRefundPayment";
    /** POS same-day void — reverses the original cash IN per payment row. */
    public static final String POS_VOID_PAYMENT = "PosVoidPayment";

    /** Sales-receipt cash tender. */
    public static final String SALES_RECEIPT = "SalesReceipt";

    /** Outbound supplier payment. */
    public static final String SUPPLIER_PAYMENT = "SupplierPayment";
}
