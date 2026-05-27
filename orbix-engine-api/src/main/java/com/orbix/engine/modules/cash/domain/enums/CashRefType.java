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

    /** Mid-shift pickup — paired OUT-TILL / IN-CASH_BOX. */
    public static final String CASH_PICKUP = "CashPickup";

    /** Petty-cash payout — single OUT-TILL. */
    public static final String PETTY_CASH = "PettyCash";

    /** End-of-day banking — paired OUT-CASH_BOX / IN-BANK. */
    public static final String BANK_DEPOSIT = "BankDeposit";

    /**
     * Compensating entry pair posted when a {@code BankDeposit} is reversed
     * (archived). Two rows under this ref_type / the deposit's id:
     *   IN  on CASH_BOX (mirror of original OUT),
     *   OUT on BANK     (mirror of original IN).
     * A new ref_type is mandatory here — direction-flip on the original
     * {@code BANK_DEPOSIT} ref_type would collide with the already-stored
     * IN+OUT pair on the {@code (ref_type, ref_id, direction)} UNIQUE.
     */
    public static final String BANK_DEPOSIT_REVERSAL = "BankDepositReversal";

    /** Supervisor cash adjustment — single entry, mandatory reason. */
    public static final String CASH_ADJUSTMENT = "CashAdjustment";

    /**
     * Compensating entry posted when a {@code CashAdjustment} is reversed
     * (archived). Direction is the opposite of the original adjustment; the
     * {@code ref_id} is the original adjustment's id. A separate ref_type
     * (rather than direction-flip on {@code CASH_ADJUSTMENT}) keeps the
     * cause-chain queryable and avoids ambiguity with future replays.
     */
    public static final String CASH_ADJUSTMENT_REVERSAL = "CashAdjustmentReversal";

    /** Gift-card issuance — single IN entry for the load proceeds. */
    public static final String GIFT_CARD_ISSUE = "GiftCardIssue";

    /** Customer-order payment (deposit / instalment / final balance). */
    public static final String ORDER_PAYMENT = "OrderPayment";
    /** Customer-order refund on cancel (per refund policy). */
    public static final String ORDER_REFUND = "OrderRefund";
}
