package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.enums.ConsumptionCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Staff canteen / display / sample draws (F2.5). Always outbound; the request
 * carries the positive qty consumed. Requires a {@code consumptionCategory}
 * (per DATA-MODEL §17.12) and an {@code authorisedByUserId} holding
 * {@code STOCK.INTERNAL_CONSUMPTION_APPROVE} or the standard supervisor role.
 */
public record PostInternalConsumptionRequestDto(
    @NotNull Long itemId,
    @NotNull Long branchId,
    @NotNull @Positive BigDecimal qty,
    @NotNull ConsumptionCategory consumptionCategory,
    @NotNull Long sectionId,
    @NotNull Long authorisedByUserId,
    @NotBlank String reason,
    Long batchId
) {}
