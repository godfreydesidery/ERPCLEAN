package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<SupplierPayment> findByCompanyIdOrderByIdDesc(Long companyId);

    List<SupplierPayment> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

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
}
