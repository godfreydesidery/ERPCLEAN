package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LpoOrderLineRepository extends JpaRepository<LpoOrderLine, Long> {

    List<LpoOrderLine> findByLpoOrderIdOrderByLineNoAsc(Long lpoOrderId);

    void deleteByLpoOrderId(Long lpoOrderId);
}
