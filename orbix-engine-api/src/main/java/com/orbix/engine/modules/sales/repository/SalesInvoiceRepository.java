package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<SalesInvoice> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SalesInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<SalesInvoice> findByCustomerIdOrderByIdDesc(Long customerId);

    /** Total outstanding debt for a customer — sum of (total - paid) on POSTED + PARTIALLY_PAID invoices. */
    @Query("""
        select coalesce(sum(s.totalAmount - s.paidAmount), 0)
          from SalesInvoice s
         where s.customerId = :customerId
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
        """)
    BigDecimal sumOutstandingDebt(@Param("customerId") Long customerId);
}
