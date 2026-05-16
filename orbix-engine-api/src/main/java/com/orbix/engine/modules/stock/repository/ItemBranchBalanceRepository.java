package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ItemBranchBalanceRepository
        extends JpaRepository<ItemBranchBalance, ItemBranchBalanceId> {

    List<ItemBranchBalance> findByBranchId(Long branchId);

    List<ItemBranchBalance> findByItemId(Long itemId);

    /** F8.1 / US-RPT-006 — items in negative on-hand for follow-up. */
    @Query("SELECT b FROM ItemBranchBalance b WHERE b.qtyOnHand < 0"
        + " AND (:branchId IS NULL OR b.branchId = :branchId)"
        + " ORDER BY b.qtyOnHand ASC")
    List<ItemBranchBalance> findNegativeOnHand(@Param("branchId") Long branchId);
}
