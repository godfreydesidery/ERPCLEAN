package com.orbix.engine.modules.pos.domain.dto;

import java.math.BigDecimal;

/**
 * Live cash-balance breakdown for an OPEN till session (ISSUE-CASH-001).
 * Mirrors the {@code expectedCashAmount} computed at close-time so the
 * cashier can reconcile before pulling the final count.
 *
 * <p>Formula: {@code expectedCash = openingFloat + cashSales − cashPickups − pettyCash}.
 * The same formula is used by {@code TillSessionServiceImpl#computeExpectedCash}.
 */
public record TillSessionBalanceDto(
    Long sessionId,
    String sessionUid,
    BigDecimal openingFloat,
    /** Net cash from POSTED SALE payments minus POSTED REFUND cash payments. */
    BigDecimal cashSales,
    /** Total cash removed from the drawer via mid-shift pickups. */
    BigDecimal cashPickups,
    /** Total petty-cash payouts from the drawer. */
    BigDecimal pettyCash,
    /** Computed expected cash in the drawer right now. */
    BigDecimal expectedCash
) {}
