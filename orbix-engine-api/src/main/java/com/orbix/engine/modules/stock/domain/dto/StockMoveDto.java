package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.domain.enums.StockMoveDirection;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;

import java.math.BigDecimal;
import java.time.Instant;

public record StockMoveDto(
    Long id,
    Instant at,
    Long itemId,
    Long branchId,
    Long companyId,
    BigDecimal qty,
    BigDecimal costAmount,
    StockMoveDirection direction,
    StockMoveType moveType,
    String refType,
    Long refId,
    Long actorId,
    String notes
) {
    public static StockMoveDto from(StockMove move) {
        return new StockMoveDto(
            move.getId(),
            move.getAt(),
            move.getItemId(),
            move.getBranchId(),
            move.getCompanyId(),
            move.getQty(),
            move.getCostAmount(),
            move.getDirection(),
            move.getMoveType(),
            move.getRefType(),
            move.getRefId(),
            move.getActorId(),
            move.getNotes()
        );
    }
}
