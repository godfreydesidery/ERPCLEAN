package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edits a sales agent and its underlying party. Party code + agent code are immutable. */
public record UpdateSalesAgentRequestDto(
    @Valid @NotNull PartyDetailsDto party,
    Long appUserId,
    @Size(max = 40) String routeCode,
    @PositiveOrZero BigDecimal commissionRate,
    @NotNull Long branchId
) {}
