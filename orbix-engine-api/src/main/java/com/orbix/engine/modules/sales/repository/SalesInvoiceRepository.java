package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<SalesInvoice> findByUid(String uid);

    List<SalesInvoice> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SalesInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<SalesInvoice> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<SalesInvoice> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);

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

    /**
     * F8.2 / US-RPT-001 — POSTED-or-later invoices (i.e. excluding DRAFT /
     * CANCELLED) for a branch on a given business date. VOIDED is included
     * so the daily report can show same-day reversals.
     */
    @Query("""
        select s from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.postedBusinessDate = :businessDate
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PAID,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.VOIDED)
         order by s.id desc
        """)
    List<SalesInvoice> findPostedOnDate(@Param("companyId") Long companyId,
                                        @Param("branchId") Long branchId,
                                        @Param("businessDate") LocalDate businessDate);

    /**
     * F8.7 / US-RPT-007 — POSTED + PARTIALLY_PAID + PAID + VOIDED invoices for
     * a customer in {@code [from, to]} (by {@code invoice_date}). Ordered by
     * date asc so the statement reads top-down chronologically.
     */
    @Query("""
        select s from SalesInvoice s
         where s.customerId = :customerId
           and s.invoiceDate between :from and :to
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PAID,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.VOIDED)
         order by s.invoiceDate asc, s.id asc
        """)
    List<SalesInvoice> findForStatement(@Param("customerId") Long customerId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    /**
     * F8.7 — opening AR balance for the statement window: sum of
     * (total − paid) on POSTED + PARTIALLY_PAID invoices dated strictly
     * before {@code from}. VOIDED + DRAFT excluded since they never
     * contributed debt.
     */
    @Query("""
        select coalesce(sum(s.totalAmount - s.paidAmount), 0)
          from SalesInvoice s
         where s.customerId = :customerId
           and s.invoiceDate < :from
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
        """)
    BigDecimal sumOutstandingBefore(@Param("customerId") Long customerId,
                                    @Param("from") LocalDate from);
}
