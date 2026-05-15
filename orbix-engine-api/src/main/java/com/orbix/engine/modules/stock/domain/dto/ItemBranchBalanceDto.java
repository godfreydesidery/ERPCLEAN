package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;

import java.math.BigDecimal;
import java.time.Instant;

public record ItemBranchBalanceDto(
    Long itemId,
    Long branchId,
    BigDecimal qtyOnHand,
    BigDecimal qtyReserved,
    BigDecimal qtyInTransit,
    BigDecimal avgCost,
    BigDecimal lastCost,
    BigDecimal reorderMin,
    BigDecimal reorderMax,
    String binLocation,
    Instant lastMovedAt
) {
    public static ItemBranchBalanceDto from(ItemBranchBalance balance) {
        return new ItemBranchBalanceDto(
            balance.getItemId(),
            balance.getBranchId(),
            balance.getQtyOnHand(),
            balance.getQtyReserved(),
            balance.getQtyInTransit(),
            balance.getAvgCost(),
            balance.getLastCost(),
            balance.getReorderMin(),
            balance.getReorderMax(),
            balance.getBinLocation(),
            balance.getLastMovedAt()
        );
    }
}
