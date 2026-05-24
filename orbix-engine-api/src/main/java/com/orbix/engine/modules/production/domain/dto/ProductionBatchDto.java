package com.orbix.engine.modules.production.domain.dto;

import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.entity.ProductionConsumption;
import com.orbix.engine.modules.production.domain.entity.ProductionOutput;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductionBatchDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long sectionId,
    Long bomId,
    Long outputItemId,
    BigDecimal plannedQty,
    BigDecimal actualQty,
    BigDecimal rejectQty,
    ProductionBatchStatus status,
    ProductionLifecycleState lifecycleState,
    Instant plannedAt,
    Instant startedAt,
    Instant completedAt,
    Instant cancelledAt,
    String notes,
    List<ProductionConsumptionDto> consumption,
    List<ProductionOutputDto> outputs
) {
    public static ProductionBatchDto from(ProductionBatch b,
                                          List<ProductionConsumption> consumption,
                                          List<ProductionOutput> outputs) {
        return new ProductionBatchDto(
            b.getId(),
            b.getUid(),
            b.getNumber(),
            b.getCompanyId(),
            b.getBranchId(),
            b.getSectionId(),
            b.getBomId(),
            b.getOutputItemId(),
            b.getPlannedQty(),
            b.getActualQty(),
            b.getRejectQty(),
            b.getStatus(),
            b.getLifecycleState(),
            b.getPlannedAt(),
            b.getStartedAt(),
            b.getCompletedAt(),
            b.getCancelledAt(),
            b.getNotes(),
            consumption.stream().map(ProductionConsumptionDto::from).toList(),
            outputs.stream().map(ProductionOutputDto::from).toList()
        );
    }
}
