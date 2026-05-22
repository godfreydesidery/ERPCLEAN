package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.PostAdjustmentRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;

/**
 * Manager-initiated stock adjustments (F2.5). Posts an {@code ADJUSTMENT} stock
 * move. Above the configured monetary threshold (or for oversells) an
 * authorising user is required.
 */
public interface AdjustmentService {

    StockMoveDto postAdjustment(PostAdjustmentRequestDto request);
}
