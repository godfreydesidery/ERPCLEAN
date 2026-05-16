package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.ProductionWastage;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionWastageRepository extends JpaRepository<ProductionWastage, Long> {

    List<ProductionWastage> findByProductionBatchIdOrderByRecordedAtAsc(Long productionBatchId);

    List<ProductionWastage> findByCategoryOrderByRecordedAtDesc(WastageCategory category);
}
