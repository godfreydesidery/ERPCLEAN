package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecordCountsRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockCountDto;

import java.util.List;

/**
 * Physical stock counts (F2.3). Lifecycle DRAFT -> IN_PROGRESS -> CLOSED ->
 * POSTED; on post, every non-zero variance becomes an ADJUSTMENT stock move.
 * Above the monetary variance threshold the post requires a separate-user
 * authoriser holding {@code STOCK.COUNT_APPROVE} (mirror of
 * {@code STOCK.ADJUST_APPROVE}).
 */
public interface StockCountService {

    List<StockCountDto> listCounts(Long branchId);

    StockCountDto getCount(String uid);

    /** Drafts a count and freezes each item's system quantity from its current balance. */
    StockCountDto createCount(CreateStockCountRequestDto request);

    StockCountDto startCount(String uid);

    StockCountDto recordCounts(String uid, RecordCountsRequestDto request);

    /** IN_PROGRESS -> CLOSED, computing per-line variances. */
    StockCountDto closeCount(String uid);

    /**
     * CLOSED -> POSTED, posting an ADJUSTMENT move for each non-zero variance.
     * Above the configured monetary threshold the request body MUST name an
     * authoriser holding {@code STOCK.COUNT_APPROVE}; under threshold the body
     * may be empty.
     */
    StockCountDto postCount(String uid, PostStockCountRequestDto request);
}
