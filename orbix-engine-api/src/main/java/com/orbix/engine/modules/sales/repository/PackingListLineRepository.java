package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.PackingListLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackingListLineRepository extends JpaRepository<PackingListLine, Long> {

    List<PackingListLine> findByPackingListIdOrderByIdAsc(Long packingListId);

    void deleteByPackingListId(Long packingListId);
}
