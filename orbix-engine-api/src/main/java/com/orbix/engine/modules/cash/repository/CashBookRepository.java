package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.CashBook;
import com.orbix.engine.modules.cash.domain.entity.CashBookId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CashBookRepository extends JpaRepository<CashBook, CashBookId> {

    List<CashBook> findByCompanyIdAndIdBusinessDate(Long companyId, LocalDate businessDate);

    List<CashBook> findByIdBranchIdAndIdBusinessDate(Long branchId, LocalDate businessDate);
}
