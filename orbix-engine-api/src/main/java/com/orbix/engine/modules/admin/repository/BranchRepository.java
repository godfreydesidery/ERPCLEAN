package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    Optional<Branch> findByUid(String uid);

    List<Branch> findByCompanyId(Long companyId);

    Optional<Branch> findByCompanyIdAndCode(Long companyId, String code);

    boolean existsByCompanyIdAndCode(Long companyId, String code);
}
