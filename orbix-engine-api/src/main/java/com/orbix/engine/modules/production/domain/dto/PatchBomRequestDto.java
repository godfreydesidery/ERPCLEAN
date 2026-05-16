package com.orbix.engine.modules.production.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * BOM patch payload (DRAFT only). Lines fully replace the existing line set.
 * Header fields are optional; null = no change.
 */
public record PatchBomRequestDto(
    @Positive BigDecimal outputQty,
    Long outputUomId,
    @PositiveOrZero BigDecimal standardYieldPct,
    String notes,
    LocalDate validFrom,
    @NotEmpty @Valid List<CreateBomRequestDto.Line> lines
) {}
