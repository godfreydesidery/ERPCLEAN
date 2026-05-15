package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockTransferLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockTransferLineRepository extends JpaRepository<StockTransferLine, Long> {

    List<StockTransferLine> findByStockTransferId(Long stockTransferId);
}
