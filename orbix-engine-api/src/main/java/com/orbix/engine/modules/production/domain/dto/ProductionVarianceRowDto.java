package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * One row of the production variance report (F7.4 / US-PROD-008 /
 * TC-PROD-021). One per {@code production_batch}; rolls up planned vs actual
 * output qty, total consumption cost, derived per-unit yield, and a wastage
 * breakdown keyed by {@link WastageCategory}.
 *
 * <p>{@code yieldPct} = actual_qty / planned_qty × 100. Null if the batch
 * never produced (CANCELLED / no output yet).
 */
public record ProductionVarianceRowDto(
    Long productionBatchId,
    String number,
    Long branchId,
    Long sectionId,
    Long bomId,
    Long outputItemId,
    BigDecimal plannedQty,
    BigDecimal actualQty,
    BigDecimal yieldPct,
    BigDecimal totalConsumptionCost,
    BigDecimal totalWastageQty,
    Map<WastageCategory, BigDecimal> wastageByCategory,
    ProductionBatchStatus status,
    ProductionLifecycleState lifecycleState,
    Instant plannedAt,
    Instant completedAt
) {}
