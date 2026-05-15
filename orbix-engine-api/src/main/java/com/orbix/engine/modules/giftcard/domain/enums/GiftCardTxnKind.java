package com.orbix.engine.modules.giftcard.domain.enums;

/** Kind of {@code gift_card_txn} ledger row. DATA-MODEL §17.7. */
public enum GiftCardTxnKind {
    /** Initial loading at issue. Always exactly one per card. */
    LOAD,
    /** Tender at POS — debits the balance. */
    REDEEM,
    /** Refund of a previously gift-card-tendered sale — credits the balance back. */
    REFUND,
    /** Auto-expiry — debits the remaining balance to zero (breakage). */
    EXPIRE
}
