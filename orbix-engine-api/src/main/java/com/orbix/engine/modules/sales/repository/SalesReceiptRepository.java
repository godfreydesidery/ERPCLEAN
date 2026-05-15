package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesReceiptRepository extends JpaRepository<SalesReceipt, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<SalesReceipt> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SalesReceipt> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);
}
