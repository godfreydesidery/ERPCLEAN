package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceListRepository extends JpaRepository<PriceList, Long> {

    List<PriceList> findByCompanyId(Long companyId);

    boolean existsByCompanyIdAndCode(Long companyId, String code);

    List<PriceList> findByCompanyIdAndIsDefaultTrue(Long companyId);
}
