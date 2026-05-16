package com.orbix.engine.modules.giftcard.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Gift-card outstanding-liability report (F8.5 / US-RPT-013). Top-level
 * envelope carries the totals the accountant needs at a glance — the
 * outstanding liability per currency for the company / branch as of an
 * optional cutoff — plus the {@code rows} drill-down by
 * {@code (status, currency, branch)}.
 *
 * <p>{@code asOf} echoes back the cutoff used (null = no cutoff = "now"),
 * so a generated PDF / spreadsheet can be dated correctly.
 */
public record GiftCardLiabilityReportDto(
    Instant asOf,
    Map<String, BigDecimal> outstandingByCurrency,
    Map<String, Integer> outstandingCountByCurrency,
    List<GiftCardLiabilityRowDto> rows
) {}
