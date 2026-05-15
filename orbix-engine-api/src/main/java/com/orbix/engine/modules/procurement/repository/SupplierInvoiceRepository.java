package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    boolean existsBySupplierIdAndSupplierInvoiceNo(Long supplierId, String supplierInvoiceNo);

    List<SupplierInvoice> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SupplierInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);
}
