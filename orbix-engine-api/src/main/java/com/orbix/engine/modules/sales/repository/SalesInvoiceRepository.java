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

    /**
     * Slice C AR-summary tile — total outstanding AR for a company / branch
     * scope. Sums (total − paid) on POSTED + PARTIALLY_PAID invoices.
     * Pass {@code branchId = null} for company-wide aggregation.
     * Backed by {@code ix_sales_invoice_branch_status} (V27).
     */
    @Query("""
        select coalesce(sum(s.totalAmount - s.paidAmount), 0)
          from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
        """)
    BigDecimal sumOutstandingForBranch(@Param("companyId") Long companyId,
                                       @Param("branchId") Long branchId);

    /**
     * Slice C AR-summary tile — count of open invoices in the
     * POSTED + PARTIALLY_PAID statuses with any outstanding balance.
     */
    @Query("""
        select count(s)
          from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
           and s.totalAmount > s.paidAmount
        """)
    long countOpenForBranch(@Param("companyId") Long companyId,
                            @Param("branchId") Long branchId);

    /**
     * Slice C AR-summary tile — overdue invoice count (POSTED / PARTIALLY_PAID
     * with {@code due_date < today} AND outstanding balance &gt; 0).
     * Backed by {@code ix_sales_invoice_branch_due} (V27).
     */
    @Query("""
        select count(s)
          from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
           and s.dueDate is not null
           and s.dueDate < :today
           and s.totalAmount > s.paidAmount
        """)
    long countOverdueForBranch(@Param("companyId") Long companyId,
                               @Param("branchId") Long branchId,
                               @Param("today") LocalDate today);

    /**
     * Slice F — paged list of "open" invoices (POSTED + PARTIALLY_PAID with
     * outstanding &gt; 0). Backed by {@code ix_sales_invoice_branch_status}.
     * {@code branchId} nullable — null = company-wide.
     */
    @Query("""
        select s from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
           and s.totalAmount > s.paidAmount
         order by s.id desc
        """)
    Page<SalesInvoice> findOpenForBranch(@Param("companyId") Long companyId,
                                         @Param("branchId") Long branchId,
                                         Pageable pageable);

    /**
     * Slice F — paged list of "overdue" invoices (OPEN + dueDate &lt; today).
     * Backed by {@code ix_sales_invoice_branch_due}. {@code branchId} nullable.
     */
    @Query("""
        select s from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
           and s.dueDate is not null
           and s.dueDate < :today
           and s.totalAmount > s.paidAmount
         order by s.id desc
        """)
    Page<SalesInvoice> findOverdueForBranch(@Param("companyId") Long companyId,
                                            @Param("branchId") Long branchId,
                                            @Param("today") LocalDate today,
                                            Pageable pageable);

    /**
     * Slice F — paged list filtered by an exact {@link com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus}.
     * {@code branchId} nullable. For the {@code OPEN} / {@code OVERDUE} bucket
     * aliases use {@link #findOpenForBranch} / {@link #findOverdueForBranch}.
     */
    Page<SalesInvoice> findByCompanyIdAndStatusOrderByIdDesc(Long companyId,
                                                             com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus status,
                                                             Pageable pageable);

    /** Slice F — same as {@link #findByCompanyIdAndStatusOrderByIdDesc} but branch-scoped. */
    Page<SalesInvoice> findByCompanyIdAndBranchIdAndStatusOrderByIdDesc(Long companyId, Long branchId,
                                                                        com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus status,
                                                                        Pageable pageable);

    /**
     * Slice G — every open AR invoice (POSTED + PARTIALLY_PAID with
     * outstanding &gt; 0) for the aging / dunning / customer-statement reports.
     * Returned unpaged because aging bucketing is computed in-memory in Java
     * for DB-portability (avoiding {@code datediff}-style native functions).
     * {@code branchId} nullable — null = company-wide.
     * Backed by {@code ix_sales_invoice_branch_due} for the predicate.
     */
    @Query("""
        select s from SalesInvoice s
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
           and s.totalAmount > s.paidAmount
         order by s.customerId asc, s.dueDate asc, s.id asc
        """)
    List<SalesInvoice> findAllOpenForAging(@Param("companyId") Long companyId,
                                           @Param("branchId") Long branchId);

    /**
     * Slice G — open AR invoices for a single customer (for the customer
     * statement / debt-position view). Sorted by dueDate asc so the oldest
     * arrears land at the top of the operator's screen.
     */
    @Query("""
        select s from SalesInvoice s
         where s.customerId = :customerId
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID)
           and s.totalAmount > s.paidAmount
         order by s.dueDate asc nulls last, s.id asc
        """)
    List<SalesInvoice> findOpenForCustomer(@Param("customerId") Long customerId);
}
