package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Layby / pre-order ageing report (F8.6 / US-RPT-014). Envelope carries:
 *
 * <ul>
 *   <li>{@code asOf} — the timestamp used for age computation (defaults to
 *       "now" when omitted); echoed so a printed report can be dated.</li>
 *   <li>{@code balanceByType} / {@code countByType} — per-type top-line
 *       outstanding for the manager's dashboard.</li>
 *   <li>{@code buckets} — per-(type, age-bucket) rollup.</li>
 *   <li>{@code orders} — flat drill-down list, sorted oldest-first so the
 *       most-stale order surfaces at the top of the chase-up list.</li>
 * </ul>
 */
public record LaybyAgeingReportDto(
    Instant asOf,
    Map<CustomerOrderType, BigDecimal> balanceByType,
    Map<CustomerOrderType, Integer> countByType,
    List<LaybyAgeingBucketDto> buckets,
    List<LaybyAgeingOrderDto> orders
) {}
