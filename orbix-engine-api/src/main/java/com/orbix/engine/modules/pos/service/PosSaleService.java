package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;

import java.util.List;

/**
 * POS sales (F5.2). POS sales are committed locally on the till and pushed to
 * the server as POSTED — there is no DRAFT lifecycle. Idempotent on
 * {@code clientOpId}: the same value pushed twice returns the original sale.
 * Posting writes outbound stock_moves per line (FEFO-split for batch-tracked
 * items via {@link com.orbix.engine.modules.stock.service.StockBatchService}).
 * Day-open enforced via {@code DayGuard.requireOpenDay} by the stock-move
 * service. Refund / void flows land with F5.5.
 */
public interface PosSaleService {

    PosSaleDto post(PostPosSaleRequestDto request);

    List<PosSaleDto> list(Long branchId);

    PosSaleDto get(Long saleId);

    List<PosSaleDto> listForSession(Long tillSessionId);
}
