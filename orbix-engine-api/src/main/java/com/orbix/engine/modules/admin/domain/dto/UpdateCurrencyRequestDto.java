package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for correcting a currency's display details. The ISO code is immutable. */
public record UpdateCurrencyRequestDto(
    @NotBlank @Size(max = 60) String name,
    @Size(max = 8) String symbol,
    @NotNull @Min(0) @Max(6) Integer minorUnitDigits
) {}
