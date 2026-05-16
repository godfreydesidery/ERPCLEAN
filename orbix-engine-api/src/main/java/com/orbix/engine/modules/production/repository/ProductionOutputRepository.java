package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.ProductionOutput;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionOutputRepository extends JpaRepository<ProductionOutput, Long> {

    List<ProductionOutput> findByProductionBatchIdOrderByLineNoAsc(Long productionBatchId);
}
