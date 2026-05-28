package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.VendorReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorReturnLineRepository extends JpaRepository<VendorReturnLine, Long> {

    List<VendorReturnLine> findByVendorReturnIdOrderByLineNoAsc(Long vendorReturnId);

    void deleteByVendorReturnId(Long vendorReturnId);
}
