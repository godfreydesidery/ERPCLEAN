package com.orbix.engine.modules.giftcard.domain.dto;

import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;

import java.math.BigDecimal;

/**
 * One bucket of the gift-card outstanding-liability rollup (F8.5 /
 * US-RPT-013). Cards are grouped by {@code (status, currency, branch)}
 * and the {@code balance} is the sum of {@code current_balance} across
 * the bucket. {@code outstandingLiability} is true for buckets that count
 * as money we still owe the bearer — ACTIVE + FROZEN. FULLY_REDEEMED /
 * EXPIRED / REFUNDED buckets surface for audit but don't drag the books.
 */
public record GiftCardLiabilityRowDto(
    GiftCardStatus status,
    String currencyCode,
    Long issuedByBranchId,
    BigDecimal balance,
    int cardCount,
    boolean outstandingLiability
) {}
