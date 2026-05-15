package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.GrnLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GrnLineRepository extends JpaRepository<GrnLine, Long> {

    List<GrnLine> findByGrnIdOrderByIdAsc(Long grnId);

    void deleteByGrnId(Long grnId);
}
