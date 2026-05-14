package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecordCountsRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockCountDto;

import java.util.List;

/**
 * Physical stock counts (F2.3). Lifecycle DRAFT -> IN_PROGRESS -> CLOSED ->
 * POSTED; on post, every non-zero variance becomes an ADJUSTMENT stock move.
 */
public interface StockCountService {

    List<StockCountDto> listCounts();

    StockCountDto getCount(Long countId);

    /** Drafts a count and freezes each item's system quantity from its current balance. */
    StockCountDto createCount(CreateStockCountRequestDto request);

    StockCountDto startCount(Long countId);

    StockCountDto recordCounts(Long countId, RecordCountsRequestDto request);

    /** IN_PROGRESS -> CLOSED, computing per-line variances. */
    StockCountDto closeCount(Long countId);

    /** CLOSED -> POSTED, posting an ADJUSTMENT move for each non-zero variance. */
    StockCountDto postCount(Long countId);
}
