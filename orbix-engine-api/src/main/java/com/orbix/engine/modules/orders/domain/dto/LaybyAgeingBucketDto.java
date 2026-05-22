package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;

import java.math.BigDecimal;

/**
 * One ageing bucket of the layby / pre-order report (F8.6 / US-RPT-014).
 * Rolls up open orders falling into the bucket's age window — count, sum
 * of total / paid / balance_due. Per-type so LAYBY and PRE_ORDER drag
 * different patterns (long-tail layby balances vs short pre-order leads).
 *
 * <p>{@code bucketLabel} is the human-readable range
 * ({@code "0-7 days"} / {@code "8-30 days"} / …). {@code minDays} +
 * {@code maxDays} are inclusive — the topmost bucket has {@code maxDays}
 * = {@link Integer#MAX_VALUE} (rendered as the label "90+ days").
 */
public record LaybyAgeingBucketDto(
    CustomerOrderType type,
    String bucketLabel,
    int minDays,
    int maxDays,
    int orderCount,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal balanceDue
) {}
