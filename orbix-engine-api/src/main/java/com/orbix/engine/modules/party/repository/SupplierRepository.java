package com.orbix.engine.modules.party.repository;

import com.orbix.engine.modules.party.domain.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    @Query("select s from Supplier s join Party p on p.id = s.partyId where p.companyId = :companyId")
    List<Supplier> findByCompanyId(@Param("companyId") Long companyId);
}
