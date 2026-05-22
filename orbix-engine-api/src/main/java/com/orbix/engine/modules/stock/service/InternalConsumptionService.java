package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.PostInternalConsumptionRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;

/**
 * Posts canteen / display / sample / donation write-offs (F2.5). Every move is
 * an outbound {@code INTERNAL_CONSUMPTION} with a non-null
 * {@code consumption_category} + {@code authorised_by_user_id}.
 */
public interface InternalConsumptionService {

    StockMoveDto postInternalConsumption(PostInternalConsumptionRequestDto request);
}
