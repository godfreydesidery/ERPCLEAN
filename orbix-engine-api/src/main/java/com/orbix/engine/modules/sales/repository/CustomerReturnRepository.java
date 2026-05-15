package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.CustomerReturn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerReturnRepository extends JpaRepository<CustomerReturn, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<CustomerReturn> findByCompanyIdOrderByIdDesc(Long companyId);

    List<CustomerReturn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);
}
