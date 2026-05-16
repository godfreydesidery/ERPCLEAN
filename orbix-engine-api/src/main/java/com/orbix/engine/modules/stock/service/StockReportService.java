package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.ItemMovementRowDto;

import java.time.LocalDate;
import java.util.List;

/**
 * Stock-side reporting (F8.1). Three reports for the storekeeper /
 * merchandiser:
 *
 * <ul>
 *   <li><b>Negative on-hand</b> (US-RPT-006) — every (item, branch) whose
 *       {@code qty_on_hand &lt; 0}; surface for investigation (oversell
 *       overrides, missed adjustments, etc.).</li>
 *   <li><b>Fast movers</b> (US-RPT-005) — top-N items by total moved qty
 *       across the selected move-type subset over a date window.</li>
 *   <li><b>Slow movers</b> (US-RPT-005) — bottom-N by the same metric;
 *       includes zero-movement items so a long-tail item that hasn't sold
 *       in the window still appears.</li>
 * </ul>
 *
 * <p>All filters are optional except date range. {@code branchId = null}
 * scopes the whole company. The default move-type set is just {@code SALE}
 * — passing additional types (e.g. {@code PROD_CONSUME},
 * {@code INTERNAL_CONSUMPTION}) broadens the throughput view.
 */
public interface StockReportService {

    List<ItemBranchBalanceDto> negativeOnHand(Long branchId);

    /** Top {@code limit} items ranked by total moved qty descending. */
    List<ItemMovementRowDto> fastMovers(Long branchId, LocalDate from, LocalDate to,
                                        List<String> moveTypes, int limit);

    /** Bottom {@code limit} items ranked by total moved qty ascending — zero-mover items included. */
    List<ItemMovementRowDto> slowMovers(Long branchId, LocalDate from, LocalDate to,
                                        List<String> moveTypes, int limit);
}
