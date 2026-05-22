package com.orbix.engine.modules.pos.domain.enums;

/** Tender methods accepted at a till. DATA-MODEL.md §7.5. */
public enum PosPaymentMethod {
    CASH,
    CARD,
    MOBILE_MONEY,
    VOUCHER,
    STORE_CREDIT,
    /** Gift-card redemption (F5.7). {@code pos_payment.reference} carries the card code. */
    GIFT_CARD
}
