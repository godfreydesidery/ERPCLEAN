package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.enums.StockMoveType;

import java.math.BigDecimal;

/**
 * Service-layer input for posting a stock move. Built by the modules that own
 * the causing document (procurement GRN, sales, POS, stock adjustments...).
 *
 * @param qty           signed — positive inbound, negative outbound
 * @param unitCost      unit cost for inbound moves; ignored for outbound (which
 *                      consume at the current moving-average)
 * @param allowOversell when true, an outbound move may drive on-hand negative
 *                      (caller must hold {@code STOCK.OVERSELL})
 * @param batchId       optional FK to {@code stock_batch}; for batch-tracked items
 *                      the caller has already chosen the batch via the FEFO picker
 *                      (or, on inbound, created the row via {@link com.orbix.engine.modules.stock.service.StockBatchService})
 */
public record PostStockMoveRequestDto(
    Long itemId,
    Long branchId,
    BigDecimal qty,
    BigDecimal unitCost,
    StockMoveType moveType,
    String refType,
    Long refId,
    String notes,
    boolean allowOversell,
    Long batchId
) {
    public PostStockMoveRequestDto(Long itemId, Long branchId, BigDecimal qty, BigDecimal unitCost,
                                   StockMoveType moveType, String refType, Long refId,
                                   String notes, boolean allowOversell) {
        this(itemId, branchId, qty, unitCost, moveType, refType, refId, notes, allowOversell, null);
    }
}
