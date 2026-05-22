package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Creates a customer. Two paths:
 * <ul>
 *   <li><b>Pick existing party</b>: supply {@code partyId} only; {@code party}
 *       is ignored.</li>
 *   <li><b>Create new party</b>: leave {@code partyId} null and supply
 *       {@code party}. The backend allocates the party code from the
 *       {@code CUST} sequence; clients cannot override it.</li>
 * </ul>
 */
public record CreateCustomerRequestDto(
    Long partyId,
    @Valid PartyDetailsDto party,
    @PositiveOrZero BigDecimal creditLimitAmount,
    @PositiveOrZero int creditTermsDays,
    Long priceListId,
    Long defaultSalesAgentId,
    Long defaultBranchId,
    boolean taxExempt
) {}
