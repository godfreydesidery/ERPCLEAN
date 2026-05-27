package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Withdraws an item's price (in a UoM and quantity tier) from a price list,
 * closing the open row the day before {@code effectiveFrom} with no replacement.
 * {@code minQty} null = the base tier (0).
 */
public record DiscontinuePriceRequestDto(
    @NotNull Long itemId,
    @NotNull Long uomId,
    @PositiveOrZero BigDecimal minQty,
    @NotNull LocalDate effectiveFrom,
    @Size(max = 2000) String reason
) {}
