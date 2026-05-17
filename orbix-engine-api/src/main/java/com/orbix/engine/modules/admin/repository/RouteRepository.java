package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Route;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RouteRepository extends JpaRepository<Route, Long> {

    List<Route> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);
}
