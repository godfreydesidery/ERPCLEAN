package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.CustomerReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerReturnRepository extends JpaRepository<CustomerReturn, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<CustomerReturn> findByUid(String uid);

    List<CustomerReturn> findByCompanyIdOrderByIdDesc(Long companyId);

    List<CustomerReturn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<CustomerReturn> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<CustomerReturn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);
}
