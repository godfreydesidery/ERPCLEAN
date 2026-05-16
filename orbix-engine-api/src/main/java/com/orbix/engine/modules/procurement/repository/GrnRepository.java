package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrnRepository extends JpaRepository<Grn, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<Grn> findByCompanyIdOrderByIdDesc(Long companyId);

    List<Grn> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    /** F7.5 EOD gate — open DRAFTs leak across days, so block close until cleared. */
    List<Grn> findByBranchIdAndStatus(Long branchId, GrnStatus status);
}
