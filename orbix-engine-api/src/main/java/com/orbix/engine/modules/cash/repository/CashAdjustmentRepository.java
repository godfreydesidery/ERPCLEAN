package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.CashAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CashAdjustmentRepository extends JpaRepository<CashAdjustment, Long> {

    /** External lookup by ULID (URL handle). */
    Optional<CashAdjustment> findByUid(String uid);

    List<CashAdjustment> findByBranchIdAndBusinessDateOrderByAtAsc(Long branchId, LocalDate businessDate);
}
