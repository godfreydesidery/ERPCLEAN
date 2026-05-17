package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteRepository extends JpaRepository<Route, Long> {

    Optional<Route> findByUid(String uid);

    List<Route> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);
}
