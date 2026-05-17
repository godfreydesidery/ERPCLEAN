package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionRepository extends JpaRepository<Section, Long> {

    Optional<Section> findByUid(String uid);

    List<Section> findByBranchId(Long branchId);

    Optional<Section> findByBranchIdAndCode(Long branchId, String code);

    boolean existsByBranchIdAndCode(Long branchId, String code);
}
