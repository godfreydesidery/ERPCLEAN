package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.ProductionWastage;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface ProductionWastageRepository extends JpaRepository<ProductionWastage, Long> {

    List<ProductionWastage> findByProductionBatchIdOrderByRecordedAtAsc(Long productionBatchId);

    List<ProductionWastage> findByCategoryOrderByRecordedAtDesc(WastageCategory category);

    /**
     * F8.3 / US-RPT-011 — wastage rows joined to their parent batch's section
     * over an Instant range. Returns {@code Object[]{sectionId, itemId, qty}}
     * — the caller multiplies by the item's avg cost to derive wastage cost
     * (best-effort; not perfect since avg cost drifts post-event).
     */
    @Query("""
        select pb.sectionId, w.itemId, sum(w.qty)
          from ProductionWastage w
          join com.orbix.engine.modules.production.domain.entity.ProductionBatch pb
            on pb.id = w.productionBatchId
         where pb.companyId = :companyId
           and (:branchId is null or pb.branchId = :branchId)
           and w.recordedAt >= :from
           and w.recordedAt < :to
         group by pb.sectionId, w.itemId
        """)
    List<Object[]> aggregateBySectionAndItem(@Param("companyId") Long companyId,
                                              @Param("branchId") Long branchId,
                                              @Param("from") Instant from,
                                              @Param("to") Instant to);

    /**
     * F8.4 / US-RPT-012 — wastage rollup grouped by (section, category, item)
     * over an Instant range for the chef-manager dashboard. Returns
     * {@code Object[]{sectionId, category, itemId, qty, count}}; the service
     * multiplies qty × item.avg_cost and folds across items to derive
     * per-(section, category) totals.
     */
    @Query("""
        select pb.sectionId, w.category, w.itemId, sum(w.qty), count(w.id)
          from ProductionWastage w
          join com.orbix.engine.modules.production.domain.entity.ProductionBatch pb
            on pb.id = w.productionBatchId
         where pb.companyId = :companyId
           and (:branchId is null or pb.branchId = :branchId)
           and (:sectionId is null or pb.sectionId = :sectionId)
           and (:category is null or w.category = :category)
           and w.recordedAt >= :from
           and w.recordedAt < :to
         group by pb.sectionId, w.category, w.itemId
        """)
    List<Object[]> aggregateBySectionCategoryItem(@Param("companyId") Long companyId,
                                                   @Param("branchId") Long branchId,
                                                   @Param("sectionId") Long sectionId,
                                                   @Param("category") WastageCategory category,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to);
}
