package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    /** The currently-effective price rows for a price list. */
    List<PriceListItem> findByPriceListIdAndValidToIsNull(Long priceListId);

    /** The single open price row for an item + UoM in a list, if one exists. */
    Optional<PriceListItem> findByPriceListIdAndItemIdAndUomIdAndValidToIsNull(
        Long priceListId, Long itemId, Long uomId);
}
