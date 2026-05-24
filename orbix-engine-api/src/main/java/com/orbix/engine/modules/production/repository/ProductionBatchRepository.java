package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductionBatchRepository extends JpaRepository<ProductionBatch, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<ProductionBatch> findByUid(String uid);

    List<ProductionBatch> findByCompanyIdOrderByIdDesc(Long companyId);

    List<ProductionBatch> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<ProductionBatch> findByCompanyIdAndSectionIdOrderByIdDesc(Long companyId, Long sectionId);

    List<ProductionBatch> findByCompanyIdAndStatusOrderByIdDesc(Long companyId,
                                                                ProductionBatchStatus status);

    /** F7.5 EOD gate — non-closed, non-cancelled batches block close. */
    List<ProductionBatch> findByBranchIdAndStatusNotAndLifecycleStateNot(
        Long branchId, ProductionBatchStatus excludedStatus,
        ProductionLifecycleState excludedLifecycleState);
}
