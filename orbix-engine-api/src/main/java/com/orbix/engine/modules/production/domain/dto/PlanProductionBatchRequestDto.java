package com.orbix.engine.modules.production.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Plan-a-batch payload (F7.3b). Service derives {@code branchId} +
 * {@code sectionId} + {@code outputItemId} from the BOM. {@code number}
 * is optional — when omitted the service generates
 * {@code BATCH-BR{branchId}-{epoch-suffix}}.
 *
 * <p>F7.3c will add a custom-production path with {@code bomId = null} +
 * explicit consumption lines.
 */
public record PlanProductionBatchRequestDto(
    String number,
    @NotNull Long bomId,
    @NotNull @Positive BigDecimal plannedQty,
    String notes
) {}
