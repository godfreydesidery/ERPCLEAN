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

    PriceListDto getPriceListByUid(String uid);

    PriceListDto createPriceList(CreatePriceListRequestDto request);

    PriceListDto updatePriceListByUid(String uid, UpdatePriceListRequestDto request);

    void archivePriceListByUid(String uid);

    void activatePriceListByUid(String uid);

    /** The currently-effective price rows on a list. */
    List<PriceListItemDto> listPricesByPriceListUid(String priceListUid);

    /** Close-and-open a price for one item + UoM on the list. */
    PriceListItemDto setPriceByPriceListUid(String priceListUid, SetPriceRequestDto request);

    /** Full price-change history for an item, newest first. */
    List<PriceChangeLogDto> priceHistoryByItemUid(String itemUid);
}
