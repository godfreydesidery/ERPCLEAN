package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface PosSaleLineRepository extends JpaRepository<PosSaleLine, Long> {

    List<PosSaleLine> findByPosSaleIdOrderByLineNoAsc(Long posSaleId);

    /**
     * F8.3 / US-RPT-011 — per-section revenue + COGS rollup over a window.
     * Joins line × parent header; groups by the header's section_id.
     * Returns {@code Object[]{sectionId, revenue, cogs, headerCount}}.
     * VOIDED sales excluded — the caller can drive SALE vs REFUND via
     * {@code kind} so a refund call subtracts cleanly.
     */
    @Query("""
        select s.sectionId,
               coalesce(sum(l.lineTotal), 0),
               coalesce(sum(l.qty * l.costAmount), 0),
               count(distinct s.id)
          from PosSale s join PosSaleLine l on l.posSaleId = s.id
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.businessDate between :from and :to
           and s.status = :status
           and s.kind = :kind
         group by s.sectionId
        """)
    List<Object[]> aggregateBySection(@Param("companyId") Long companyId,
                                      @Param("branchId") Long branchId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to,
                                      @Param("status") PosSaleStatus status,
                                      @Param("kind") PosSaleKind kind);
}
