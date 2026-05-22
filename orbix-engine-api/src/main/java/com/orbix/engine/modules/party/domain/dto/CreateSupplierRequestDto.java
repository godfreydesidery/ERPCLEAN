package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates a supplier. Two paths:
 * <ul>
 *   <li><b>Pick existing party</b>: supply {@code partyId} only; {@code party}
 *       is ignored.</li>
 *   <li><b>Create new party</b>: leave {@code partyId} null and supply
 *       {@code party}. The backend allocates the party code from the
 *       {@code SUP} sequence; clients cannot override it.</li>
 * </ul>
 */
public record CreateSupplierRequestDto(
    Long partyId,
    @Valid PartyDetailsDto party,
    @PositiveOrZero int paymentTermsDays,
    @PositiveOrZero BigDecimal creditLimitAmount,
    @Size(max = 3) String defaultCurrencyCode,
    @Size(max = 120) String bankName,
    @Size(max = 40) String bankAccountNo,
    Integer leadTimeDays
) {}
