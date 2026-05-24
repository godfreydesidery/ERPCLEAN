package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UomRepository extends JpaRepository<Uom, Long> {

    Optional<Uom> findByUid(String uid);

    boolean existsByCode(String code);

    /** The current base unit(s) of a dimension — used to keep at most one. */
    List<Uom> findByDimensionAndBaseTrue(UomDimension dimension);
}
