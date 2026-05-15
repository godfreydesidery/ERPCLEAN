package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload for editing a VAT group. The code is immutable. */
public record UpdateVatGroupRequestDto(
    @NotBlank @Size(max = 80) String name,
    @NotNull @PositiveOrZero @DecimalMax("1.0000") BigDecimal rate,
    @NotNull LocalDate validFrom,
    boolean isDefault
) {}
