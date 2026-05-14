package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Payload for editing a price list. The code is immutable. */
public record UpdatePriceListRequestDto(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
    @NotNull LocalDate validFrom,
    LocalDate validTo,
    boolean isDefault,
    boolean taxInclusive
) {}
