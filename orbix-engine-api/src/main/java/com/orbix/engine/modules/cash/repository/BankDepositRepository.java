package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.BankDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BankDepositRepository extends JpaRepository<BankDeposit, Long> {

    /** External lookup by ULID (URL handle). */
    Optional<BankDeposit> findByUid(String uid);

    List<BankDeposit> findByBranchIdAndBusinessDateOrderByAtAsc(Long branchId, LocalDate businessDate);
}
