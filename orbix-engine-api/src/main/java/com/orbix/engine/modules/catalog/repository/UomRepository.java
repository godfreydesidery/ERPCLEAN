package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.Uom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {

    Optional<Uom> findByCode(String code);

    boolean existsByCode(String code);
}
