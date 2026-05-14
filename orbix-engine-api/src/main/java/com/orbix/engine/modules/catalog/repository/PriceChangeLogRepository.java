package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.PriceChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PriceChangeLogRepository extends JpaRepository<PriceChangeLog, Long> {

    /** Full price-change history for an item, across every list/UoM, newest first. */
    @Query("""
        select log from PriceChangeLog log
        where log.priceListItemId in (
            select pli.id from PriceListItem pli where pli.itemId = :itemId
        )
        order by log.changedAt desc
        """)
    List<PriceChangeLog> findByItemId(@Param("itemId") Long itemId);
}
