package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Edits a supplier and its underlying party. The party code is immutable. */
public record UpdateSupplierRequestDto(
    @Valid @NotNull PartyDetailsDto party,
    @PositiveOrZero int paymentTermsDays,
    @PositiveOrZero BigDecimal creditLimitAmount,
    @Size(max = 3) String defaultCurrencyCode,
    @Size(max = 120) String bankName,
    @Size(max = 40) String bankAccountNo,
    Integer leadTimeDays
) {}
