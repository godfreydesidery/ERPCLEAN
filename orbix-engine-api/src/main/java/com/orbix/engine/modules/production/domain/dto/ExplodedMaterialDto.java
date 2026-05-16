package com.orbix.engine.modules.production.domain.dto;

import java.math.BigDecimal;

/**
 * One flat material requirement produced by the BOM explosion (F7.3b).
 * Sub-BOMs are recursively expanded; each terminal {@code input_item_id}
 * appears once with its total {@code qty} (already scaled by the batch
 * planned-vs-output ratio and bumped by per-line wastage_pct).
 */
public record ExplodedMaterialDto(
    Long inputItemId,
    Long uomId,
    BigDecimal qty
) {}
