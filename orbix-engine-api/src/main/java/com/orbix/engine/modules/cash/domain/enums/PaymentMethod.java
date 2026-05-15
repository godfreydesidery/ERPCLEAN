package com.orbix.engine.modules.cash.domain.enums;

/** How money moves on a supplier / customer payment. DATA-MODEL.md §10.4 / §10.6. */
public enum PaymentMethod {
    CASH,
    BANK_TRANSFER,
    CHEQUE,
    MOBILE_MONEY
}
