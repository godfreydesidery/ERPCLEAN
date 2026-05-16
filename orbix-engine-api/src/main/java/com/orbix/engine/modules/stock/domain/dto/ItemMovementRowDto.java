package com.orbix.engine.modules.stock.domain.dto;

import java.math.BigDecimal;

/**
 * One row of the fast / slow movers report (F8.1 / US-RPT-005). Captures
 * total moved qty (sum of ABS(qty) across the chosen move-type subset) for
 * a single item over the window plus the item's current on-hand for
 * decision-making (low-mover + low-stock is the most actionable).
 */
public record ItemMovementRowDto(
    Long itemId,
    String itemCode,
    String itemName,
    BigDecimal movedQty,
    BigDecimal qtyOnHand
) {}
