package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.ConversionDto;
import com.orbix.engine.modules.production.domain.dto.CreateConversionRequestDto;
import com.orbix.engine.modules.production.domain.enums.ConversionStatus;

import java.util.List;

/**
 * Item conversion (F7.4). Creates the row in DRAFT, posts paired
 * PROD_CONSUME (outbound from_item) + PROD_OUTPUT (inbound to_item)
 * stock_moves in one transaction. Same-day-only — no time-shift posting.
 */
public interface ConversionService {

    ConversionDto createDraft(CreateConversionRequestDto request);

    /** DRAFT -> POSTED. Writes the paired stock_moves; terminal. */
    ConversionDto post(Long conversionId);

    /** DRAFT -> CANCELLED. */
    ConversionDto cancel(Long conversionId);

    ConversionDto get(Long conversionId);

    List<ConversionDto> list(Long branchId, ConversionStatus status);
}
