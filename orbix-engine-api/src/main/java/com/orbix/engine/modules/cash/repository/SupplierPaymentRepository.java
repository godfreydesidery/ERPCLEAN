package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<SupplierPayment> findByUid(String uid);

    List<SupplierPayment> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SupplierPayment> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<SupplierPayment> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<SupplierPayment> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);

    /**
     * F8.7 / US-RPT-007 — POSTED payments for a supplier in {@code [from, to]}
     * (by {@code payment_date}) for the AP statement.
     */
    @Query("""
        select p from SupplierPayment p
         where p.supplierId = :supplierId
           and p.paymentDate between :from and :to
           and p.status = com.orbix.engine.modules.cash.domain.enums.SupplierPaymentStatus.POSTED
         order by p.paymentDate asc, p.id asc
        """)
    List<SupplierPayment> findForStatement(@Param("supplierId") Long supplierId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to);

    /** F8.7 — total POSTED payment value dated strictly before {@code from}. */
    @Query("""
        select coalesce(sum(p.totalAmount), 0) from SupplierPayment p
         where p.supplierId = :supplierId
           and p.paymentDate < :from
           and p.status = com.orbix.engine.modules.cash.domain.enums.SupplierPaymentStatus.POSTED
        """)
    BigDecimal sumPaymentsBefore(@Param("supplierId") Long supplierId,
                                  @Param("from") LocalDate from);

    /**
     * Slice G.1 — recent POSTED supplier payments for the AP supplier-statement
     * drill-down. {@code fromDate} is inclusive; caller passes {@code today - 30d}.
     * Ordered payment_date desc, id desc (newest first). Backed by supplierId index.
     */
    @Query("""
        select p from SupplierPayment p
         where p.supplierId = :supplierId
           and p.paymentDate >= :fromDate
           and p.status = com.orbix.engine.modules.cash.domain.enums.SupplierPaymentStatus.POSTED
         order by p.paymentDate desc, p.id desc
        """)
    List<SupplierPayment> findRecentPostedForSupplier(@Param("supplierId") Long supplierId,
                                                      @Param("fromDate") LocalDate fromDate,
                                                      Pageable pageable);
}
