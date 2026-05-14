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
    boolean allowOversell
) {}
