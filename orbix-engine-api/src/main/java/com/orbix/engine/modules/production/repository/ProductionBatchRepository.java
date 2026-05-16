package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<ProductionBatch> findByCompanyIdOrderByIdDesc(Long companyId);

    List<ProductionBatch> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<ProductionBatch> findByCompanyIdAndSectionIdOrderByIdDesc(Long companyId, Long sectionId);

    List<ProductionBatch> findByCompanyIdAndStatusOrderByIdDesc(Long companyId,
                                                                ProductionBatchStatus status);
}
