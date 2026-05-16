package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockCountRepository extends JpaRepository<StockCount, Long> {

    List<StockCount> findByCompanyIdOrderByCountDateDesc(Long companyId);

    List<StockCount> findByCompanyIdAndBranchIdOrderByCountDateDesc(Long companyId, Long branchId);

    boolean existsByBranchIdAndNumber(Long branchId, String number);
}
