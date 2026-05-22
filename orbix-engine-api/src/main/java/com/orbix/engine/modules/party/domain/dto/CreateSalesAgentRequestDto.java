package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates a sales agent. If a party in the company already carries the supplied
 * {@code party.tin()}, that party is reused (shared-party rule) and {@code code}
 * is ignored.
 */
public record CreateSalesAgentRequestDto(
    @NotBlank @Size(max = 40) String code,
    @Valid @NotNull PartyDetailsDto party,
    @NotBlank @Size(max = 40) String agentCode,
    Long appUserId,
    @Size(max = 40) String routeCode,
    @PositiveOrZero BigDecimal commissionRate,
    @NotNull Long branchId
) {}
