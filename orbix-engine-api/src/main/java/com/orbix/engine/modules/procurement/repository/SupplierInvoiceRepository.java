package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    boolean existsBySupplierIdAndSupplierInvoiceNo(Long supplierId, String supplierInvoiceNo);

    List<SupplierInvoice> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SupplierInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    /**
     * F8.7 / US-RPT-007 — POSTED-or-later invoices for a supplier in
     * {@code [from, to]} (by {@code invoice_date}) for the AP statement.
     */
    @Query("""
        select s from SupplierInvoice s
         where s.supplierId = :supplierId
           and s.invoiceDate between :from and :to
           and s.status in (
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.POSTED,
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.PARTIALLY_PAID,
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.PAID)
         order by s.invoiceDate asc, s.id asc
        """)
    List<SupplierInvoice> findForStatement(@Param("supplierId") Long supplierId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** F8.7 — opening AP balance: sum (total − paid) on POSTED + PARTIALLY_PAID dated before {@code from}. */
    @Query("""
        select coalesce(sum(s.totalAmount - s.paidAmount), 0)
          from SupplierInvoice s
         where s.supplierId = :supplierId
           and s.invoiceDate < :from
           and s.status in (
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.POSTED,
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.PARTIALLY_PAID)
        """)
    BigDecimal sumOutstandingBefore(@Param("supplierId") Long supplierId,
                                     @Param("from") LocalDate from);
}
