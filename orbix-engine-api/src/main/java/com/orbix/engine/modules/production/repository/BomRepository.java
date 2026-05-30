package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.Bom;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BomRepository extends JpaRepository<Bom, Long> {

    Optional<Bom> findByUid(String uid);

    Optional<Bom> findByOutputItemIdAndVersion(Long outputItemId, Integer version);

    Optional<Bom> findTopByOutputItemIdOrderByVersionDesc(Long outputItemId);

    List<Bom> findByCompanyIdOrderByIdDesc(Long companyId);

    List<Bom> findByCompanyIdAndSectionIdOrderByIdDesc(Long companyId, Long sectionId);

    List<Bom> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, BomStatus status);

    List<Bom> findByCompanyIdAndOutputItemIdOrderByVersionDesc(Long companyId, Long outputItemId);

    /**
     * Guard for section deactivation (F7.3): counts BOMs that are ACTIVE or DRAFT
     * (i.e. not RETIRED) for the given section.  A non-zero result blocks deactivation
     * so that production recipes are not silently orphaned.
     */
    @Query("""
        select count(b) from Bom b
        where b.sectionId = :sectionId
          and b.status in :statuses
        """)
    long countBySectionIdAndStatusIn(
        @Param("sectionId") Long sectionId,
        @Param("statuses") java.util.Collection<BomStatus> statuses);
}
