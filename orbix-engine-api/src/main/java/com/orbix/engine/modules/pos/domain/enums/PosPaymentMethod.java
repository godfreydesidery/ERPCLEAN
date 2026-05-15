package com.orbix.engine.modules.pos.domain.enums;

/** Tender methods accepted at a till. DATA-MODEL.md §7.5. */
public enum PosPaymentMethod {
    CASH,
    CARD,
    MOBILE_MONEY,
    VOUCHER,
    STORE_CREDIT
}
