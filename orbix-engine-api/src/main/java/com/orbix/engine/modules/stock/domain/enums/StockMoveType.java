package com.orbix.engine.modules.stock.domain.enums;

/** What caused a stock move. DATA-MODEL.md §4.1. */
public enum StockMoveType {
    GRN,
    SALE,
    RETURN_IN,
    RETURN_OUT,
    DAMAGE,
    ADJUSTMENT,
    TRANSFER_OUT,
    TRANSFER_IN,
    /** Production batch: raw-material consumption. */
    PROD_CONSUME,
    /** Production batch: finished-goods output. */
    PROD_OUTPUT,
    /** Item conversion: source item consumed. Distinct from PROD_CONSUME so
     *  conversions are independently filterable in the stock ledger. */
    CONV_CONSUME,
    /** Item conversion: target item output. Distinct from PROD_OUTPUT for the
     *  same reason. */
    CONV_OUTPUT,
    OPENING,
    EXPIRY_WRITE_OFF,
    INTERNAL_CONSUMPTION,
    STAFF_PURCHASE,
    EMPLOYEE_GIFT,
    RESERVED
}
