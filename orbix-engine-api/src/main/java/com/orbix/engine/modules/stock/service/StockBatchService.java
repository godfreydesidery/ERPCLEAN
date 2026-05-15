package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.BatchPickDto;
import com.orbix.engine.modules.stock.domain.dto.CreateStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecallStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Per-batch inventory rows for batch-tracked items (F2.4). Created on inbound
 * (GRN, production output), drained FEFO on consumption, flagged EXPIRED by a
 * scheduled job, and RECALLED on demand. DATA-MODEL.md §17.5.
 */
public interface StockBatchService {

    /** Create a new ACTIVE batch row for an inbound document. */
    StockBatchDto createBatch(CreateStockBatchRequestDto request);

    /**
     * FEFO picker — chooses ACTIVE batches in earliest-expiry order to cover the
     * given outbound qty, draining each batch and flipping to EXHAUSTED when it
     * hits zero. Throws if the active on-hand across batches is insufficient.
     */
    List<BatchPickDto> drainFefo(Long itemId, Long branchId, BigDecimal qty);

    /**
     * Flips ACTIVE batches whose {@code expiry_at &lt; asOf} to EXPIRED, writes
     * EXPIRY_WRITE_OFF stock moves for the remaining on-hand and emits one
     * {@code StockBatchExpired.v1} event per batch. Returns the number of
     * batches flipped.
     */
    int markExpired(LocalDate asOf);

    /**
     * Recalls an ACTIVE batch — writes off the remaining on-hand as an
     * EXPIRY_WRITE_OFF stock move, flips the status, and emits
     * {@code BatchRecalled.v1}.
     */
    StockBatchDto recallBatch(Long batchId, RecallStockBatchRequestDto request);

    List<StockBatchDto> listBatches(Long branchId, Long itemId, StockBatchStatus status);

    /** Active batches whose expiry falls on or before {@code today + daysAhead}. */
    List<StockBatchDto> listExpiringSoon(Long branchId, int daysAhead);

    StockBatchDto getBatch(Long batchId);
}
