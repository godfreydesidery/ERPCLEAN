package com.orbix.engine.modules.pos.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Per-branch on-hand qty snapshot (F5.4). Pulled by the till at session open
 * (and periodically while connected) so cashiers see a realistic stock figure
 * while offline. The till uses this as a soft cap — the server enforces the
 * hard `STOCK.OVERSELL` rule at push time.
 */
public record BalanceSnapshotDto(
    Instant snapshotAt,
    Long branchId,
    List<Row> balances
) {
    public record Row(Long itemId, BigDecimal qtyOnHand) {}
}
