package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.day.service.EodBlockerDto;
import com.orbix.engine.modules.day.service.EodGuard;
import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import com.orbix.engine.modules.production.repository.ProductionBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Production EOD gate (F7.5 / TC-DAY-009). Any batch that is neither
 * CANCELLED nor lifecycle-CLOSED blocks close — operator must finish the run
 * (post output → optional donate / write-off → close) or cancel a PLANNED
 * batch outright.
 */
@Component
@RequiredArgsConstructor
public class ProductionEodGuard implements EodGuard {

    private final ProductionBatchRepository batches;

    @Override
    @Transactional(readOnly = true)
    public List<EodBlockerDto> check(Long branchId, LocalDate businessDate) {
        return batches
            .findByBranchIdAndStatusNotAndLifecycleStateNot(branchId,
                ProductionBatchStatus.CANCELLED, ProductionLifecycleState.CLOSED)
            .stream()
            .map(this::toBlocker)
            .toList();
    }

    @Override
    public String moduleName() {
        return "production";
    }

    private EodBlockerDto toBlocker(ProductionBatch batch) {
        String kind = batch.getStatus() == ProductionBatchStatus.PLANNED
            ? "PLANNED_BATCH"
            : batch.getStatus() == ProductionBatchStatus.IN_PROGRESS
                ? "IN_PROGRESS_BATCH"
                : "OPEN_OUTPUT_BATCH";
        String message = "Production batch " + batch.getNumber()
            + " is " + batch.getStatus() + " / " + batch.getLifecycleState()
            + " — advance to CLOSED or cancel before day-end";
        return new EodBlockerDto(moduleName(), kind, "ProductionBatch", batch.getId(), message);
    }
}
