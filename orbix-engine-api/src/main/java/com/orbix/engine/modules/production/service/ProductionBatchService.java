package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.AdvanceLifecycleRequestDto;
import com.orbix.engine.modules.production.domain.dto.PlanProductionBatchRequestDto;
import com.orbix.engine.modules.production.domain.dto.PostProductionOutputRequestDto;
import com.orbix.engine.modules.production.domain.dto.ProductionBatchDto;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;

import java.util.List;

/**
 * Production batch lifecycle (F7.3b + F7.3c). Plan / start / record-output /
 * cancel + lifecycle advancement + close.
 */
public interface ProductionBatchService {

    /**
     * Plan a batch — explodes the BOM, reserves materials via
     * {@link com.orbix.engine.modules.stock.service.StockReservationService},
     * writes one {@code production_consumption} row per exploded material.
     * Throws if any material has insufficient availability at reserve time.
     */
    ProductionBatchDto plan(PlanProductionBatchRequestDto request);

    /**
     * Start the batch — releases each reservation and posts a corresponding
     * PROD_CONSUME stock_move at the moving-average cost. Captures
     * {@code unit_cost} on each {@code production_consumption} row.
     */
    ProductionBatchDto start(Long batchId);

    /**
     * Record finished output — posts one PROD_OUTPUT stock_move per output
     * line (creating a {@code stock_batch} first for batch-tracked items).
     * Computes per-line {@code unit_cost} from
     * {@code sum(consumption_cost) ÷ sum(output_qty)}. Sets status to
     * COMPLETED and {@code lifecycle_state} to OUTPUT_HOT_DISPLAY.
     */
    ProductionBatchDto postOutput(Long batchId, PostProductionOutputRequestDto request);

    /**
     * Cancel a PLANNED batch — releases the reservation. Once a batch is
     * IN_PROGRESS use the lifecycle write-off path (advance to OUTPUT_DONATED
     * or OUTPUT_WRITE_OFF) instead.
     */
    ProductionBatchDto cancel(Long batchId);

    /**
     * Advance the fine-grained {@code lifecycle_state} dimension (F7.3c /
     * US-PROD-010). Forward-only HOT → COLD → DISCOUNTED; OUTPUT_DONATED /
     * OUTPUT_WRITE_OFF reachable from any OUTPUT_* state — those trigger a
     * {@link com.orbix.engine.modules.stock.service.StockBatchService#recallBatch}
     * write-off on every batch-tracked output to clear the remaining on-hand.
     */
    ProductionBatchDto advanceLifecycle(Long batchId, AdvanceLifecycleRequestDto request);

    /**
     * Close a COMPLETED batch — terminal, immutable thereafter. Typically
     * called at end-of-day after all output is disposed (sold / donated /
     * written off).
     */
    ProductionBatchDto close(Long batchId);

    ProductionBatchDto get(Long batchId);

    List<ProductionBatchDto> list(Long branchId, Long sectionId, ProductionBatchStatus status);
}
