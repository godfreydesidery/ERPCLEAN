package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SupplierInvoiceRepository extends JpaRepository<SupplierInvoice, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    boolean existsBySupplierIdAndSupplierInvoiceNo(Long supplierId, String supplierInvoiceNo);

    Optional<SupplierInvoice> findByUid(String uid);

    List<SupplierInvoice> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SupplierInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<SupplierInvoice> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<SupplierInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);

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

    /**
     * Slice G.1 — every open AP invoice (POSTED + PARTIALLY_PAID with
     * outstanding &gt; 0) for the AP aging / dunning / supplier-statement reports.
     * Returned unpaged because aging bucketing is computed in-memory in Java
     * for DB-portability. {@code branchId} nullable — null = company-wide.
     * Backed by {@code ix_supplier_invoice_branch_due} (V72).
     */
    @Query("""
        select s from SupplierInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.POSTED,
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.PARTIALLY_PAID)
           and s.totalAmount > s.paidAmount
         order by s.supplierId asc, s.dueDate asc, s.id asc
        """)
    List<SupplierInvoice> findAllOpenForAging(@Param("companyId") Long companyId,
                                              @Param("branchId") Long branchId);

    /**
     * Slice G.1 — open AP invoices for a single supplier (for the supplier
     * statement / debt-position view). Sorted by dueDate asc so the oldest
     * arrears land at the top of the operator's screen. Capped at 100 rows.
     */
    @Query("""
        select s from SupplierInvoice s
         where s.supplierId = :supplierId
           and s.status in (
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.POSTED,
              com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.PARTIALLY_PAID)
           and s.totalAmount > s.paidAmount
         order by s.dueDate asc, s.id asc
        """)
    List<SupplierInvoice> findOpenForSupplier(@Param("supplierId") Long supplierId,
                                              Pageable pageable);
}
