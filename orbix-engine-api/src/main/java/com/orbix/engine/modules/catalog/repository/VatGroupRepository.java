package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VatGroupRepository extends JpaRepository<VatGroup, Long> {

    List<VatGroup> findByCompanyId(Long companyId);

    Optional<VatGroup> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    List<VatGroup> findByCompanyIdAndIsDefaultTrue(Long companyId);
}
