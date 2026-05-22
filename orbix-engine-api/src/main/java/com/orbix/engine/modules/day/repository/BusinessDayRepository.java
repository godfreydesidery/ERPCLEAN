package com.orbix.engine.modules.day.repository;

import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.entity.BusinessDayId;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessDayRepository extends JpaRepository<BusinessDay, BusinessDayId> {

    /** The single non-closed day for a branch, if one exists (OPEN or CLOSING). */
    Optional<BusinessDay> findFirstByBranchIdAndStatusIn(Long branchId, List<BusinessDayStatus> statuses);

    Optional<BusinessDay> findFirstByBranchIdAndStatus(Long branchId, BusinessDayStatus status);

    List<BusinessDay> findByBranchIdOrderByBusinessDateDesc(Long branchId);
}
