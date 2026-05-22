package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.ItemGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ItemGroupRepository extends JpaRepository<ItemGroup, Long> {

    Optional<ItemGroup> findByUid(String uid);

    List<ItemGroup> findByCompanyId(Long companyId);

    Optional<ItemGroup> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);
}
