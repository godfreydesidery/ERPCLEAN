package com.orbix.engine.modules.production.domain.enums;

/**
 * Conversion lifecycle. DATA-MODEL §9.6.
 *
 * <ul>
 *   <li>{@code DRAFT} — written but not yet committed to the stock ledger.</li>
 *   <li>{@code POSTED} — paired PROD_CONSUME + PROD_OUTPUT stock_moves
 *       written; terminal.</li>
 *   <li>{@code CANCELLED} — allowed only from DRAFT.</li>
 * </ul>
 */
public enum ConversionStatus {
    DRAFT,
    POSTED,
    CANCELLED
}
