package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemBranchBalanceRepository
        extends JpaRepository<ItemBranchBalance, ItemBranchBalanceId> {

    List<ItemBranchBalance> findByBranchId(Long branchId);

    List<ItemBranchBalance> findByItemId(Long itemId);
}
