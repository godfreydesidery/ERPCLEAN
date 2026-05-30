package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface StockMoveRepository extends JpaRepository<StockMove, Long> {

    Page<StockMove> findByCompanyId(Long companyId, Pageable pageable);

    Page<StockMove> findByCompanyIdAndBranchId(Long companyId, Long branchId, Pageable pageable);

    /** Stock card: every move for an item in a branch, oldest first. */
    Page<StockMove> findByItemIdAndBranchIdOrderByAtAsc(Long itemId, Long branchId, Pageable pageable);

    /**
     * F8.1 / US-RPT-005 — aggregated movement per item in a window, filtered
     * by move type. Returns {@code Object[]{itemId, totalQty, moveCount, lastMoveAt}}
     * where {@code totalQty} is the sum of ABS(qty) so both inbound and outbound
     * rows contribute to total throughput, {@code moveCount} is the row count, and
     * {@code lastMoveAt} is the most recent move timestamp in the window.
     */
    @Query("SELECT m.itemId, SUM(ABS(m.qty)), COUNT(m), MAX(m.at) FROM StockMove m"
        + " WHERE m.companyId = :companyId"
        + " AND (:branchId IS NULL OR m.branchId = :branchId)"
        + " AND m.moveType IN :moveTypes"
        + " AND m.at >= :from AND m.at < :to"
        + " GROUP BY m.itemId")
    List<Object[]> aggregateMovementByItem(@Param("companyId") Long companyId,
                                            @Param("branchId") Long branchId,
                                            @Param("moveTypes") List<StockMoveType> moveTypes,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    /**
     * Stock-card opening balance — sum of all move quantities for the given
     * item+branch whose id is strictly less than {@code beforeId}. Used to
     * seed the running-balance accumulator when paginating the stock card so
     * that page 2+ does not reset to zero (ISSUE-STOCK-001).
     *
     * <p>Returns {@code null} when there are no qualifying rows (no prior
     * moves); callers must treat {@code null} as zero.
     */
    @Query("SELECT COALESCE(SUM(m.qty), 0) FROM StockMove m"
        + " WHERE m.itemId = :itemId AND m.branchId = :branchId AND m.id < :beforeId")
    BigDecimal sumQtyBeforeId(@Param("itemId") Long itemId,
                               @Param("branchId") Long branchId,
                               @Param("beforeId") Long beforeId);
}
