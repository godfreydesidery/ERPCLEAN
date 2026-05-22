package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockCountLine;

import java.math.BigDecimal;

public record StockCountLineDto(
    Long id,
    Long itemId,
    BigDecimal systemQty,
    BigDecimal countedQty,
    BigDecimal varianceQty,
    String note
) {
    public static StockCountLineDto from(StockCountLine line) {
        return new StockCountLineDto(line.getId(), line.getItemId(), line.getSystemQty(),
            line.getCountedQty(), line.getVarianceQty(), line.getNote());
    }
}
