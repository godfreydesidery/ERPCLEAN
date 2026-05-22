package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface StockMoveRepository extends JpaRepository<StockMove, Long> {

    Page<StockMove> findByCompanyId(Long companyId, Pageable pageable);

    Page<StockMove> findByCompanyIdAndBranchId(Long companyId, Long branchId, Pageable pageable);

    /** Stock card: every move for an item in a branch, oldest first. */
    Page<StockMove> findByItemIdAndBranchIdOrderByAtAsc(Long itemId, Long branchId, Pageable pageable);

    /**
     * F8.1 / US-RPT-005 — sum of moved qty per (itemId) in a window, filtered
     * by move type so the merchandiser can rank fast / slow movers across
     * outbound sales (or any chosen subset). Sum uses {@code ABS(qty)} so
     * outbound rows (negative qty) and inbound rows (positive) both add to
     * total throughput. Returns {@code Object[]{itemId, totalQty}}.
     */
    @Query("SELECT m.itemId, SUM(ABS(m.qty)) FROM StockMove m"
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
}
