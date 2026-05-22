package com.orbix.engine.modules.production.repository;

import com.orbix.engine.modules.production.domain.entity.BomLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomLineRepository extends JpaRepository<BomLine, Long> {

    List<BomLine> findByBomIdOrderByLineNoAsc(Long bomId);

    void deleteByBomId(Long bomId);

    List<BomLine> findBySubBomId(Long subBomId);
}
