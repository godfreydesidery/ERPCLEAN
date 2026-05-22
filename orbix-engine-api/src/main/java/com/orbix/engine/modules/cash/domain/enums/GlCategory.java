package com.orbix.engine.modules.cash.domain.enums;

/**
 * GL classification stamped on each {@code cash_entry} — drives the
 * accounting-export integration but is opaque to the rest of the system.
 * See cash/README.md §10 + Phase 1.1 additions.
 */
public enum GlCategory {
    CASH,
    BANK,
    PETTY,
    VARIANCE,
    SUPPLIER_SETTLEMENT,
    RECEIPT,
    TILL_FLOAT,
    CASH_REFUND,
    GIFT_CARD_ISSUE_PROCEEDS,
    FX_VARIANCE,
    ORDER_DEPOSIT,
    ADJUSTMENT
}
