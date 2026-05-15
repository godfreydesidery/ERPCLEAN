package com.orbix.engine.modules.stock.domain.enums;

/**
 * Category stamped on {@code stock_move.consumption_category} — required for
 * {@code INTERNAL_CONSUMPTION} moves. Drives the per-category consumption
 * report. DATA-MODEL.md §17.12.
 */
public enum ConsumptionCategory {
    CANTEEN,
    DISPLAY,
    SAMPLES,
    DONATION,
    MAINTENANCE,
    OTHER
}
