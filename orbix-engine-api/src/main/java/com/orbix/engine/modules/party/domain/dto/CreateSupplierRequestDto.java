package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Creates a supplier. If a party in the company already carries the supplied
 * {@code party.tin()}, that party is reused (shared-party rule) and {@code code}
 * is ignored.
 */
public record CreateSupplierRequestDto(
    @NotBlank @Size(max = 40) String code,
    @Valid @NotNull PartyDetailsDto party,
    @PositiveOrZero int paymentTermsDays,
    @PositiveOrZero BigDecimal creditLimitAmount,
    @Size(max = 3) String defaultCurrencyCode,
    @Size(max = 120) String bankName,
    @Size(max = 40) String bankAccountNo,
    Integer leadTimeDays
) {}
