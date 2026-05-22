package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {

    Optional<PriceList> findByUid(String uid);

    List<PriceList> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    List<PriceList> findByCompanyIdAndIsDefaultTrue(Long companyId);
}
