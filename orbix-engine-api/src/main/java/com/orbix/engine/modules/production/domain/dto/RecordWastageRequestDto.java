package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Record-wastage payload (F7.3c / US-PROD-009). Mandatory {@code reason}
 * captures the why for audit. {@code uomId} defaults to the item's UoM.
 */
public record RecordWastageRequestDto(
    @NotNull Long productionBatchId,
    @NotNull Long itemId,
    @NotNull @Positive BigDecimal qty,
    Long uomId,
    @NotNull WastageCategory category,
    @NotBlank String reason
) {}
