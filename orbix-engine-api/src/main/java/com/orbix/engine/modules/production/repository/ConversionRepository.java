package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.Conversion;
import com.orbix.engine.modules.production.domain.enums.ConversionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversionRepository extends JpaRepository<Conversion, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<Conversion> findByCompanyIdOrderByIdDesc(Long companyId);

    List<Conversion> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<Conversion> findByCompanyIdAndStatusOrderByIdDesc(Long companyId, ConversionStatus status);
}
