package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.AdjustPricesRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.CopyPricesRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.DiscontinuePriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceChangeLogDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Price-list maintenance (F1.5). Prices are versioned by {@code [validFrom,
 * validTo]} windows and quantity tiers ({@code minQty}); setting a price closes
 * the prior open {@code price_list_item} row and opens a new one, appending to
 * the append-only {@code price_change_log}. Emits {@code ItemPriceChanged.v1} /
 * {@code ItemPriceDiscontinued.v1}.
 */
public interface PriceListService {

    // ---- price lists -------------------------------------------------------
    List<PriceListDto> listPriceLists();

    PriceListDto getPriceListByUid(String uid);

    /** Look a price list up by its (company-unique) code, e.g. "RETAIL". */
    PriceListDto getPriceListByCode(String code);

    PriceListDto createPriceList(CreatePriceListRequestDto request);

    PriceListDto updatePriceListByUid(String uid, UpdatePriceListRequestDto request);

    void archivePriceListByUid(String uid);

    void activatePriceListByUid(String uid);

    // ---- prices ------------------------------------------------------------
    /** Prices on a list effective on {@code asOf} (null = today). */
    List<PriceListItemDto> listPricesByPriceListUid(String priceListUid, LocalDate asOf);

    /** Resolve the single price for item + UoM + quantity effective on a date. */
    PriceListItemDto resolvePrice(String priceListUid, Long itemId, Long uomId, BigDecimal qty, LocalDate asOf);

    /** Close-and-open a price for one item + UoM + tier on the list. */
    PriceListItemDto setPriceByPriceListUid(String priceListUid, SetPriceRequestDto request);

    /** Withdraw a price (close the open row, no replacement). */
    void discontinuePriceByPriceListUid(String priceListUid, DiscontinuePriceRequestDto request);

    // ---- bulk --------------------------------------------------------------
    /** Copy another list's effective prices into this one (optionally % shifted). Returns rows written. */
    int copyPricesIntoPriceListUid(String priceListUid, CopyPricesRequestDto request);

    /** Shift every effective price on this list by a percentage. Returns rows written. */
    int adjustPricesByPriceListUid(String priceListUid, AdjustPricesRequestDto request);

    // ---- audit -------------------------------------------------------------
    /** Full price-change history for an item, newest first. */
    List<PriceChangeLogDto> priceHistoryByItemUid(String itemUid);
}
