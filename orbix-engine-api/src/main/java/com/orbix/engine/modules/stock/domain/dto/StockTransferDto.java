package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockTransfer;
import com.orbix.engine.modules.stock.domain.entity.StockTransferLine;
import com.orbix.engine.modules.stock.domain.enums.StockTransferStatus;

import java.time.Instant;
import java.util.List;

public record StockTransferDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long fromBranchId,
    Long toBranchId,
    Instant issuedAt,
    Instant receivedAt,
    StockTransferStatus status,
    List<StockTransferLineDto> lines
) {
    public static StockTransferDto from(StockTransfer transfer, List<StockTransferLine> lines) {
        return new StockTransferDto(
            transfer.getId(), transfer.getUid(), transfer.getNumber(), transfer.getCompanyId(),
            transfer.getFromBranchId(), transfer.getToBranchId(), transfer.getIssuedAt(),
            transfer.getReceivedAt(), transfer.getStatus(),
            lines.stream().map(StockTransferLineDto::from).toList());
    }
}
