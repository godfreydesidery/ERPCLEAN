package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LpoOrderRepository extends JpaRepository<LpoOrder, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<LpoOrder> findByCompanyIdOrderByIdDesc(Long companyId);

    List<LpoOrder> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, LpoOrderStatus status);

    List<LpoOrder> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);
}
