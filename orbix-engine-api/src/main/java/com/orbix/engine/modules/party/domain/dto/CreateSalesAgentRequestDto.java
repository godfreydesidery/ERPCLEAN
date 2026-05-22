package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates a sales agent. Two paths:
 * <ul>
 *   <li><b>Pick existing party</b>: supply {@code partyId} only; {@code party}
 *       is ignored.</li>
 *   <li><b>Create new party</b>: leave {@code partyId} null and supply
 *       {@code party}. The backend allocates a fresh party code from the
 *       {@code AGT} sequence; clients cannot override it. If a party in the
 *       company already carries the supplied {@code party.tin()}, that party
 *       is reused (shared-party rule) and the allocated code goes unused.</li>
 * </ul>
 */
public record CreateSalesAgentRequestDto(
    Long partyId,
    @Valid PartyDetailsDto party,
    @NotBlank @Size(max = 40) String agentCode,
    Long appUserId,
    Long routeId,
    @PositiveOrZero BigDecimal commissionRate,
    @NotNull Long branchId
) {}
