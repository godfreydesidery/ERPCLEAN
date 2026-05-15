package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CloseTillSessionRequestDto(
    @NotNull @PositiveOrZero BigDecimal declaredCashAmount,
    /** Required when |variance| exceeds the configured threshold; must hold POS.SESSION_VARIANCE_APPROVE. */
    Long supervisorId,
    String notes
) {}
