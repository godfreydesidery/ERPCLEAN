package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganisationRepository extends JpaRepository<Organisation, Long> {
}
