package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LpoOrderRepository extends JpaRepository<LpoOrder, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<LpoOrder> findByUid(String uid);

    List<LpoOrder> findByCompanyIdOrderByIdDesc(Long companyId);

    List<LpoOrder> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, LpoOrderStatus status);

    List<LpoOrder> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<LpoOrder> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<LpoOrder> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);
}
