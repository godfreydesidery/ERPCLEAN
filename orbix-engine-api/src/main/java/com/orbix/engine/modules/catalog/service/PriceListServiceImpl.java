package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceChangeLogDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.PriceChangeLog;
import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceChangeLogRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceListServiceImpl implements PriceListService {

    private final PriceListRepository priceLists;
    private final PriceListItemRepository priceListItems;
    private final PriceChangeLogRepository priceChangeLog;
    private final ItemRepository items;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<PriceListDto> listPriceLists() {
        return priceLists.findByCompanyId(context.companyId()).stream()
            .sorted(Comparator.comparing(PriceList::getCode))
            .map(PriceListDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListDto getPriceListByUid(String uid) {
        return PriceListDto.from(requirePriceListByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "PriceList")
    public PriceListDto createPriceList(CreatePriceListRequestDto request) {
        Long companyId = context.companyId();
        String code = request.code().trim().toUpperCase();
        if (priceLists.existsByCompanyIdAndCode(companyId, code)) {
            throw new IllegalArgumentException("Price list code already exists: " + code);
        }
        PriceList list = priceLists.save(new PriceList(companyId, code, request.name(),
            request.currencyCode(), request.validFrom(), request.validTo(),
            request.isDefault(), request.taxInclusive(), context.userId()));
        if (request.isDefault()) {
            clearOtherDefaults(companyId, list.getId());
        }
        return PriceListDto.from(list);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "PriceList")
    public PriceListDto updatePriceListByUid(String uid, UpdatePriceListRequestDto request) {
        PriceList list = requirePriceListByUid(uid);
        Long actorId = context.userId();
        list.update(request.name(), request.currencyCode(), request.validFrom(),
            request.validTo(), request.taxInclusive(), actorId);
        list.setAsDefault(request.isDefault(), actorId);
        if (request.isDefault()) {
            clearOtherDefaults(list.getCompanyId(), list.getId());
        }
        return PriceListDto.from(list);
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "PriceList")
    public void archivePriceListByUid(String uid) {
        PriceList list = requirePriceListByUid(uid);
        if (list.getStatus() == ItemStatus.ARCHIVED) {
            throw new IllegalArgumentException("Price list is already archived: " + uid);
        }
        list.archive(context.userId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceListItemDto> listPricesByPriceListUid(String priceListUid) {
        PriceList list = requirePriceListByUid(priceListUid);
        return priceListItems.findByPriceListIdAndValidToIsNull(list.getId()).stream()
            .map(PriceListItemDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "SET_PRICE", entityType = "PriceList")
    public PriceListItemDto setPriceByPriceListUid(String priceListUid, SetPriceRequestDto request) {
        PriceList list = requirePriceListByUid(priceListUid);
        requireItemById(request.itemId());
        Long priceListId = list.getId();

        Optional<PriceListItem> current = priceListItems
            .findByPriceListIdAndItemIdAndUomIdAndValidToIsNull(
                priceListId, request.itemId(), request.uomId());

        BigDecimal oldPrice = null;
        if (current.isPresent()) {
            PriceListItem prior = current.get();
            if (!request.effectiveFrom().isAfter(prior.getValidFrom())) {
                throw new IllegalArgumentException(
                    "New price must take effect after the current price's start date");
            }
            oldPrice = prior.getPrice();
            prior.closeOn(request.effectiveFrom());
        }

        PriceListItem newRow = priceListItems.save(new PriceListItem(
            priceListId, request.itemId(), request.uomId(), request.price(), request.effectiveFrom()));
        priceChangeLog.save(new PriceChangeLog(newRow.getId(), oldPrice, request.price(),
            request.effectiveFrom(), context.userId(), request.reason()));

        events.publish("ItemPriceChanged.v1", "Item", String.valueOf(request.itemId()),
            Map.of("itemId", request.itemId(), "priceListId", priceListId,
                "uomId", request.uomId(), "price", request.price()));
        return PriceListItemDto.from(newRow);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PriceChangeLogDto> priceHistoryByItemUid(String itemUid) {
        Item item = requireItemByUid(itemUid);
        return priceChangeLog.findByItemId(item.getId()).stream()
            .map(PriceChangeLogDto::from)
            .toList();
    }

    private void clearOtherDefaults(Long companyId, Long keepId) {
        Long actorId = context.userId();
        priceLists.findByCompanyIdAndIsDefaultTrue(companyId).stream()
            .filter(l -> !l.getId().equals(keepId))
            .forEach(l -> l.setAsDefault(false, actorId));
    }

    private PriceList requirePriceListByUid(String uid) {
        PriceList list = priceLists.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Price list not found: " + uid));
        if (!Objects.equals(list.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Price list not found: " + uid);
        }
        return list;
    }

    private Item requireItemByUid(String uid) {
        Item item = items.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + uid));
        if (!Objects.equals(item.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item not found: " + uid);
        }
        return item;
    }

    private Item requireItemById(Long itemId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }
}
