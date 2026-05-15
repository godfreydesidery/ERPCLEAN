package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record OpenTillSessionRequestDto(
    @NotNull Long tillId,
    @NotNull @PositiveOrZero BigDecimal openingFloatAmount
) {}
