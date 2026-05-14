package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    List<StockTransfer> findByCompanyIdOrderByIdDesc(Long companyId);

    boolean existsByCompanyIdAndNumber(Long companyId, String number);
}
