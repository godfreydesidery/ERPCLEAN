package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.VendorReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorReturnRepository extends JpaRepository<VendorReturn, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<VendorReturn> findByUid(String uid);

    Page<VendorReturn> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<VendorReturn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);
}
