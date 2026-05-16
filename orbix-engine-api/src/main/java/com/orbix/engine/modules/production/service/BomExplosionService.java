package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.ExplodedMaterialDto;

import java.math.BigDecimal;
import java.util.List;

/**
 * BOM explosion (F7.3b). Recursively walks {@code bom_line.sub_bom_id}
 * pointers down to raw materials, scaling each terminal qty by the
 * effective batch factor and bumping by per-line {@code wastage_pct}.
 *
 * <p>If a sub-BOM is referenced, its lines are scaled by
 * {@code parent_line_qty / sub_bom.output_qty} and recursively exploded.
 * Multiple paths to the same {@code input_item_id} are summed.
 */
public interface BomExplosionService {

    /**
     * Explode {@code bomId} for a batch producing {@code plannedOutputQty}
     * units of the BOM's output item. Returns one row per terminal material
     * with its total required quantity.
     *
     * @throws IllegalArgumentException if a sub-BOM cycle is encountered
     *     (defensive — activation already ran cycle detection) or if a path
     *     resolves to an inactive sub-BOM
     */
    List<ExplodedMaterialDto> explode(Long bomId, BigDecimal plannedOutputQty);
}
