package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.Bom;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
