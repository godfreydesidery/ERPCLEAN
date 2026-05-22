package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.Till;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TillRepository extends JpaRepository<Till, Long> {

    boolean existsByBranchIdAndCode(Long branchId, String code);

    List<Till> findByCompanyIdOrderByIdAsc(Long companyId);

    List<Till> findByCompanyIdAndBranchIdOrderByIdAsc(Long companyId, Long branchId);
}
