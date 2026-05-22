package com.orbix.engine.modules.sales.domain.enums;

/** CASH = paid at invoice time; CREDIT = customer pays later (debt opens). DATA-MODEL.md §6.3. */
public enum PaymentTerms {
    CASH,
    CREDIT
}
