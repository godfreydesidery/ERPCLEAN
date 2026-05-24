package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.Till;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TillRepository extends JpaRepository<Till, Long> {

    Optional<Till> findByUid(String uid);

    boolean existsByBranchIdAndCode(Long branchId, String code);

    List<Till> findByCompanyIdOrderByIdAsc(Long companyId);

    List<Till> findByCompanyIdAndBranchIdOrderByIdAsc(Long companyId, Long branchId);
}
