package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.BankDeposit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BankDepositRepository extends JpaRepository<BankDeposit, Long> {

    List<BankDeposit> findByBranchIdAndBusinessDateOrderByAtAsc(Long branchId, LocalDate businessDate);
}
