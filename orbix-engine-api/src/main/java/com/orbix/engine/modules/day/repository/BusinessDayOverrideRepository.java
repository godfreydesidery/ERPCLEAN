package com.orbix.engine.modules.day.repository;

import com.orbix.engine.modules.day.domain.entity.BusinessDayOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessDayOverrideRepository extends JpaRepository<BusinessDayOverride, Long> {

    List<BusinessDayOverride> findByBranchIdOrderByAtDesc(Long branchId);
}
