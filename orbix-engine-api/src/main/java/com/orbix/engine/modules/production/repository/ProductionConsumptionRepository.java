package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.ProductionConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionConsumptionRepository extends JpaRepository<ProductionConsumption, Long> {

    List<ProductionConsumption> findByProductionBatchIdOrderByLineNoAsc(Long productionBatchId);
}
