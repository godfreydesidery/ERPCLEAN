package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Edits a customer and its underlying party. The party code is immutable. */
public record UpdateCustomerRequestDto(
    @Valid @NotNull PartyDetailsDto party,
    @PositiveOrZero BigDecimal creditLimitAmount,
    @PositiveOrZero int creditTermsDays,
    Long priceListId,
    Long defaultSalesAgentId,
    Long defaultBranchId,
    boolean taxExempt
) {}
