package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.GrnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface GrnLineRepository extends JpaRepository<GrnLine, Long> {

    List<GrnLine> findByGrnIdOrderByIdAsc(Long grnId);

    void deleteByGrnId(Long grnId);

    /**
     * F8.8 / US-NFR-COMP-001 — per-VAT-group input rollup over a
     * {@code [from, to]} window driven by {@code grn.received_date}.
     * Only POSTED GRNs (DRAFT excluded). Returns
     * {@code Object[]{vatGroupId, rate, sumNet}} — net = {@code line_total}
     * (GRN lines store pre-tax line total) and the service computes
     * input VAT as {@code net × rate} at the row level to match how
     * the GRN posting service rolls header tax.
     */
    @Query("""
        select l.vatGroupId,
               v.rate,
               coalesce(sum(l.lineTotal), 0)
          from GrnLine l
               join Grn g on l.grnId = g.id
               join com.orbix.engine.modules.catalog.domain.entity.VatGroup v on v.id = l.vatGroupId
         where g.companyId = :companyId
           and (:branchId is null or g.branchId = :branchId)
           and g.receivedDate between :from and :to
           and g.status = com.orbix.engine.modules.procurement.domain.enums.GrnStatus.POSTED
         group by l.vatGroupId, v.rate
        """)
    List<Object[]> aggregateInputVat(@Param("companyId") Long companyId,
                                     @Param("branchId") Long branchId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);
}
