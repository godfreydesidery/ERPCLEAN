package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Populates the target price list from the currently-effective prices of
 * {@code sourcePriceListUid}, optionally shifted by {@code adjustPct}
 * (e.g. 5 = +5%, -10 = -10%; null/0 = exact copy). Source and target must share
 * the same currency. {@code approverId} is required when |adjustPct| exceeds the
 * approval threshold.
 */
public record CopyPricesRequestDto(
    @NotNull @ValidUlid String sourcePriceListUid,
    BigDecimal adjustPct,
    @NotNull LocalDate effectiveFrom,
    @Size(max = 2000) String reason,
    Long approverId
) {}
