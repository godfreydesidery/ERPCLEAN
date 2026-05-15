package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<SupplierPayment> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SupplierPayment> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);
}
