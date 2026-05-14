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
    PROD_CONSUME,
    PROD_OUTPUT,
    OPENING
}
