package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SalesReceiptRepository extends JpaRepository<SalesReceipt, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<SalesReceipt> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SalesReceipt> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    Page<SalesReceipt> findByCompanyIdOrderByIdDesc(Long companyId, Pageable pageable);

    Page<SalesReceipt> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId, Pageable pageable);

    /**
     * F8.7 / US-RPT-007 — POSTED receipts for a customer in
     * {@code [from, to]} (by {@code receipt_date}), date asc for the
     * statement timeline.
     */
    @Query("""
        select r from SalesReceipt r
         where r.customerId = :customerId
           and r.receiptDate between :from and :to
           and r.status = com.orbix.engine.modules.sales.domain.enums.SalesReceiptStatus.POSTED
         order by r.receiptDate asc, r.id asc
        """)
    List<SalesReceipt> findForStatement(@Param("customerId") Long customerId,
                                         @Param("from") LocalDate from,
                                         @Param("to") LocalDate to);

    /** F8.7 — total POSTED receipt value dated strictly before {@code from} (opening credit). */
    @Query("""
        select coalesce(sum(r.totalAmount), 0) from SalesReceipt r
         where r.customerId = :customerId
           and r.receiptDate < :from
           and r.status = com.orbix.engine.modules.sales.domain.enums.SalesReceiptStatus.POSTED
        """)
    BigDecimal sumReceiptsBefore(@Param("customerId") Long customerId,
                                  @Param("from") LocalDate from);
}
