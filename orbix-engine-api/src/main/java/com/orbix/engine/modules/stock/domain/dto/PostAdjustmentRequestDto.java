package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Manager-initiated stock adjustment (F2.5). Signed qty — positive = found,
 * negative = shrinkage. Above the configured monetary threshold an
 * {@code authorisedByUserId} is required (and must hold {@code STOCK.ADJUST_APPROVE}).
 */
public record PostAdjustmentRequestDto(
    @NotNull Long itemId,
    @NotNull Long branchId,
    @NotNull BigDecimal qty,
    BigDecimal unitCost,
    @NotBlank String reason,
    Long sectionId,
    Long batchId,
    Long authorisedByUserId,
    boolean allowOversell
) {}
