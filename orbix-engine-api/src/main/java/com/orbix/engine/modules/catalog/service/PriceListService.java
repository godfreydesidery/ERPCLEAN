package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceChangeLogDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;

import java.util.List;

/**
 * Price-list maintenance (F1.5). Setting a price closes the prior open
 * {@code price_list_item} row and opens a new one, appending to the
 * append-only {@code price_change_log}. Emits {@code ItemPriceChanged.v1}.
 */
public interface PriceListService {

    List<PriceListDto> listPriceLists();

    PriceListDto getPriceList(Long priceListId);

    PriceListDto createPriceList(CreatePriceListRequestDto request);

    PriceListDto updatePriceList(Long priceListId, UpdatePriceListRequestDto request);

    void archivePriceList(Long priceListId);

    /** The currently-effective price rows on a list. */
    List<PriceListItemDto> listPrices(Long priceListId);

    /** Close-and-open a price for one item + UoM on the list. */
    PriceListItemDto setPrice(Long priceListId, SetPriceRequestDto request);

    /** Full price-change history for an item, newest first. */
    List<PriceChangeLogDto> priceHistoryForItem(Long itemId);
}
