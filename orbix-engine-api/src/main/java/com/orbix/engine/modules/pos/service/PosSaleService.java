package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto;

import java.util.List;

/**
 * POS sales (F5.2 / F5.3). POS sales are committed locally on the till and
 * pushed to the server as POSTED — there is no DRAFT lifecycle. Idempotent on
 * {@code clientOpId}: the same value pushed twice returns the original sale.
 * Posting writes outbound stock_moves per line (FEFO-split for batch-tracked
 * items via {@link com.orbix.engine.modules.stock.service.StockBatchService}).
 * Day-open enforced via {@code DayGuard.requireOpenDay} by the stock-move
 * service.
 *
 * <p>F5.3 layers a per-line discount-threshold rule (above
 * {@code orbix.pos.discount-threshold-pct} the request needs a
 * {@code discountApproverId} holding {@code POS.DISCOUNT_APPROVE}) and a
 * same-business-day void path that writes compensating {@code RETURN_IN}
 * moves. Refund-with-cash-out lands with F5.5.
 */
public interface PosSaleService {

    PosSaleDto post(PostPosSaleRequestDto request);

    /**
     * Same-business-day void of a POSTED sale (F5.3). Writes compensating
     * {@code RETURN_IN} stock moves at the snapped line cost. Rejects batch-
     * tracked items for now (restore-to-original-batch is a later refinement)
     * and any sale on a business day different from the till session's
     * current open day.
     */
    PosSaleDto voidSale(Long saleId, VoidPosSaleRequestDto request);

    List<PosSaleDto> list(Long branchId);

    PosSaleDto get(Long saleId);

    List<PosSaleDto> listForSession(Long tillSessionId);
}
