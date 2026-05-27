package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Shifts every currently-effective price on a list by {@code adjustPct}
 * (e.g. 5 = +5%, -10 = -10%), opening fresh rows from {@code effectiveFrom}.
 * {@code approverId} is required when |adjustPct| exceeds the approval threshold.
 */
public record AdjustPricesRequestDto(
    @NotNull BigDecimal adjustPct,
    @NotNull LocalDate effectiveFrom,
    @Size(max = 2000) String reason,
    Long approverId
) {}
