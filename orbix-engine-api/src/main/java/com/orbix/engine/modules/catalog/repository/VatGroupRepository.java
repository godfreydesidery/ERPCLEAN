package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VatGroupRepository extends JpaRepository<VatGroup, Long> {

    Optional<VatGroup> findByUid(String uid);

    List<VatGroup> findByCompanyId(Long companyId);

    Optional<VatGroup> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    List<VatGroup> findByCompanyIdAndIsDefaultTrue(Long companyId);

    /** Sync pull delta: vat_groups whose change_seq is above the cursor watermark. */
    @Query("select v from VatGroup v where v.companyId = :companyId and v.changeSeq > :cursor order by v.changeSeq asc")
    List<VatGroup> findByCompanyIdAndChangeSeqGreaterThan(@Param("companyId") Long companyId,
                                                          @Param("cursor") Long cursor,
                                                          Pageable pageable);
}
