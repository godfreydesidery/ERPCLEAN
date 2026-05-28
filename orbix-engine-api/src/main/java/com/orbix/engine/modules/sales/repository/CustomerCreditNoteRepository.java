package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CustomerCreditNoteRepository extends JpaRepository<CustomerCreditNote, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<CustomerCreditNote> findByUid(String uid);

    List<CustomerCreditNote> findByCustomerReturnId(Long customerReturnId);

    List<CustomerCreditNote> findByCompanyIdOrderByIdDesc(Long companyId);

    /**
     * F8.7 — POSTED credit notes for a customer in {@code [from, to]}
     * (by {@code cn_date}). Subtract from AR.
     */
    @Query("""
        select c from CustomerCreditNote c
         where c.customerId = :customerId
           and c.cnDate between :from and :to
           and c.status = com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus.POSTED
         order by c.cnDate asc, c.id asc
        """)
    List<CustomerCreditNote> findForStatement(@Param("customerId") Long customerId,
                                               @Param("from") LocalDate from,
                                               @Param("to") LocalDate to);

    /** F8.7 — total POSTED credit-note value dated strictly before {@code from}. */
    @Query("""
        select coalesce(sum(c.totalAmount), 0) from CustomerCreditNote c
         where c.customerId = :customerId
           and c.cnDate < :from
           and c.status = com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus.POSTED
        """)
    BigDecimal sumCreditNotesBefore(@Param("customerId") Long customerId,
                                     @Param("from") LocalDate from);
}
