package com.orbix.engine.modules.day.repository;

import com.orbix.engine.modules.day.domain.entity.BusinessDayOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessDayOverrideRepository extends JpaRepository<BusinessDayOverride, Long> {

    /** External lookup by ULID (URL handle). */
    Optional<BusinessDayOverride> findByUid(String uid);

    List<BusinessDayOverride> findByBranchIdOrderByAtDesc(Long branchId);
}
