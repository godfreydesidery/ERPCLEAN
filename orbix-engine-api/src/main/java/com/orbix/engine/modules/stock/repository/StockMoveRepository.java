package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockMove;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMoveRepository extends JpaRepository<StockMove, Long> {

    Page<StockMove> findByCompanyId(Long companyId, Pageable pageable);

    Page<StockMove> findByCompanyIdAndBranchId(Long companyId, Long branchId, Pageable pageable);

    /** Stock card: every move for an item in a branch, oldest first. */
    Page<StockMove> findByItemIdAndBranchIdOrderByAtAsc(Long itemId, Long branchId, Pageable pageable);
}
