package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * The stock ledger (F2.2). {@code stock_move} is append-only and the source of
 * truth; {@code item_branch_balance} is maintained atomically with each move —
 * moving-average cost on inbound, consume-at-average on outbound, with a
 * negative-stock guard. Posting requires the branch's business day to be OPEN.
 */
public interface StockMoveService {

    /** Posts one stock move and updates the (item, branch) balance in the same transaction. */
    StockMoveDto post(PostStockMoveRequestDto request);

    /** Company-scoped move ledger; {@code branchId} optional filter. */
    PageDto<StockMoveDto> listMoves(Long branchId, Pageable pageable);

    /**
     * Current balances for a branch. Slice F drill-through filters:
     * {@code negativeOnly} keeps rows with {@code qtyOnHand &lt; 0};
     * {@code belowReorderOnly} keeps rows where {@code reorderMin != null}
     * and {@code qtyOnHand &lt;= reorderMin}. Both flags compose (AND).
     */
    List<ItemBranchBalanceDto> listBalances(Long branchId, boolean negativeOnly, boolean belowReorderOnly);

    /** Stock card: every move for an item in a branch, oldest first. */
    PageDto<StockMoveDto> stockCard(Long itemId, Long branchId, Pageable pageable);

    /**
     * Cross-module read of the (item, branch) balance row — for callers that
     * need the moving-average cost without crossing the boundary into
     * {@code stock.domain.entity..} or {@code stock.repository..}
     * (ADR-0004 §5: cross-module reaches stay on the {@code *Service}
     * interface seam). Empty when no balance row exists yet (e.g. first
     * sale of a non-batch item).
     */
    Optional<ItemBranchBalanceDto> findBalance(Long itemId, Long branchId);
}
