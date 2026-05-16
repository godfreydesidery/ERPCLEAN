package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.PlanProductionBatchRequestDto;
import com.orbix.engine.modules.production.domain.dto.PostProductionOutputRequestDto;
import com.orbix.engine.modules.production.domain.dto.ProductionBatchDto;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;

import java.util.List;

/**
 * Production batch lifecycle (F7.3b). Plan / start / record-output / cancel.
 * Lifecycle states + wastage + close land in F7.3c.
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
     * IN_PROGRESS use the lifecycle write-off path in F7.3c instead.
     */
    ProductionBatchDto cancel(Long batchId);

    ProductionBatchDto get(Long batchId);

    List<ProductionBatchDto> list(Long branchId, Long sectionId, ProductionBatchStatus status);
}
