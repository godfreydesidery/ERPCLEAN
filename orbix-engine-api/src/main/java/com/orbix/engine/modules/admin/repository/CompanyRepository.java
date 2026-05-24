package com.orbix.engine.modules.admin.repository;

import com.orbix.engine.modules.admin.domain.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByOrganisationIdAndCode(Long organisationId, String code);

    boolean existsByOrganisationIdAndCode(Long organisationId, String code);

    /** True when any company transacts in this currency (i.e. it is a functional currency). */
    boolean existsByCurrencyCode(String currencyCode);
}
