package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockTransferLine;

import java.math.BigDecimal;

public record StockTransferLineDto(
    Long id,
    Long itemId,
    BigDecimal issuedQty,
    BigDecimal receivedQty,
    BigDecimal costAmount
) {
    public static StockTransferLineDto from(StockTransferLine line) {
        return new StockTransferLineDto(line.getId(), line.getItemId(), line.getIssuedQty(),
            line.getReceivedQty(), line.getCostAmount());
    }
}
