package com.orbix.engine.modules.stock.domain.enums;

/** Lifecycle of a {@code stock_batch} row. DATA-MODEL.md §17.5. */
public enum StockBatchStatus {
    ACTIVE,
    EXHAUSTED,
    EXPIRED,
    RECALLED
}
