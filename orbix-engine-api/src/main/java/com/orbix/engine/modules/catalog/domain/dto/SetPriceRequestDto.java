package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Sets the price of an item (in a UoM) on a price list. Closes the prior open
 * row the day before {@code effectiveFrom} and opens a fresh one.
 */
public record SetPriceRequestDto(
    @NotNull Long itemId,
    @NotNull Long uomId,
    @NotNull @PositiveOrZero BigDecimal price,
    @NotNull LocalDate effectiveFrom,
    @Size(max = 2000) String reason
) {}
