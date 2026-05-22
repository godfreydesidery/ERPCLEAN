package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockBatchDto(
    Long id,
    Long itemId,
    Long branchId,
    Long companyId,
    String batchNo,
    LocalDate manufacturedAt,
    LocalDate expiryAt,
    BigDecimal qtyReceived,
    BigDecimal qtyOnHand,
    BigDecimal cost,
    String sourceDocType,
    Long sourceDocId,
    StockBatchStatus status
) {
    public static StockBatchDto from(StockBatch b) {
        return new StockBatchDto(
            b.getId(),
            b.getItemId(),
            b.getBranchId(),
            b.getCompanyId(),
            b.getBatchNo(),
            b.getManufacturedAt(),
            b.getExpiryAt(),
            b.getQtyReceived(),
            b.getQtyOnHand(),
            b.getCost(),
            b.getSourceDocType(),
            b.getSourceDocId(),
            b.getStatus()
        );
    }
}
