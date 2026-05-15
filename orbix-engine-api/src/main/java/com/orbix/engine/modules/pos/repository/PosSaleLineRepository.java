package com.orbix.engine.modules.pos.repository;

import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PosSaleLineRepository extends JpaRepository<PosSaleLine, Long> {

    List<PosSaleLine> findByPosSaleIdOrderByLineNoAsc(Long posSaleId);
}
