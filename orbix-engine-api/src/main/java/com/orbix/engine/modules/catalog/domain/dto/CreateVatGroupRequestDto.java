package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code rate} is a fraction (0.18 = 18%). */
public record CreateVatGroupRequestDto(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 80) String name,
    @NotNull @PositiveOrZero @DecimalMax("1.0000") BigDecimal rate,
    @NotNull LocalDate validFrom,
    boolean isDefault
) {}
