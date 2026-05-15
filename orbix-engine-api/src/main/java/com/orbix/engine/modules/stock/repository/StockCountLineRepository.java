package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockCountLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockCountLineRepository extends JpaRepository<StockCountLine, Long> {

    List<StockCountLine> findByStockCountId(Long stockCountId);
}
