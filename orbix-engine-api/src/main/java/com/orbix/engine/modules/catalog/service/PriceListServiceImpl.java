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
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.PriceChangeLog;
import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceChangeLogRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListRepository;
import com.orbix.engine.modules.catalog.repository.UomRepository;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PriceListServiceImpl implements PriceListService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MONEY_SCALE = 4;
    private static final String PRICE_APPROVE_PERMISSION = "PRICE.APPROVE";

    private final PriceListRepository priceLists;
    private final PriceListItemRepository priceListItems;
    private final PriceChangeLogRepository priceChangeLog;
    private final ItemRepository items;
    private final UomRepository uoms;
    private final EventPublisher events;
    private final RequestContext context;
    private final SettingsService settings;
    private final PermissionResolverService permissions;

    // ---- price lists -------------------------------------------------------

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
    @Transactional(readOnly = true)
    public PriceListDto getPriceListByCode(String code) {
        PriceList list = priceLists.findByCompanyIdAndCode(context.companyId(), code.trim().toUpperCase())
            .orElseThrow(() -> new NoSuchElementException("Price list not found: " + code));
        return PriceListDto.from(list);
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
        requireValidWindow(request.validFrom(), request.validTo());
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
        requireValidWindow(request.validFrom(), request.validTo());
        if (!list.getCurrencyCode().equals(request.currencyCode())
            && priceListItems.existsByPriceListId(list.getId())) {
            throw new IllegalArgumentException(
                "Cannot change the currency of a price list that already has prices");
        }
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
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "PriceList")
    public void activatePriceListByUid(String uid) {
        PriceList list = requirePriceListByUid(uid);
        if (list.getStatus() == ItemStatus.ACTIVE) {
            throw new IllegalArgumentException("Price list is already active: " + uid);
        }
        list.activate(context.userId());
    }

    // ---- prices ------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PriceListItemDto> listPricesByPriceListUid(String priceListUid, LocalDate asOf) {
        PriceList list = requirePriceListByUid(priceListUid);
        LocalDate on = asOf != null ? asOf : LocalDate.now();
        return enrich(priceListItems.findEffective(list.getId(), on));
    }

    @Override
    @Transactional(readOnly = true)
    public PriceListItemDto resolvePrice(String priceListUid, Long itemId, Long uomId,
                                         BigDecimal qty, LocalDate asOf) {
        PriceList list = requirePriceListByUid(priceListUid);
        LocalDate on = asOf != null ? asOf : LocalDate.now();
        BigDecimal q = qty != null ? qty : BigDecimal.ONE;
        if (list.getStatus() != ItemStatus.ACTIVE
            || on.isBefore(list.getValidFrom())
            || (list.getValidTo() != null && on.isAfter(list.getValidTo()))) {
            throw new NoSuchElementException(
                "Price list " + list.getCode() + " is not effective on " + on);
        }
        PriceListItem row = priceListItems.findEffectiveTiers(list.getId(), itemId, uomId, q, on).stream()
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException(
                "No effective price for item " + itemId + " (uom " + uomId + ", qty " + q + ") on " + on));
        return toDto(row, items.findById(itemId).orElse(null), uoms.findById(uomId).orElse(null));
    }

    @Override
    @Transactional
    @Auditable(action = "SET_PRICE", entityType = "PriceList")
    public PriceListItemDto setPriceByPriceListUid(String priceListUid, SetPriceRequestDto request) {
        PriceList list = requirePriceListByUid(priceListUid);
        requireActive(list);
        Item item = requireItemById(request.itemId());
        Uom uom = requireUomById(request.uomId());
        BigDecimal minQty = normalizeMinQty(request.minQty());
        requireWithinWindow(list, request.effectiveFrom());

        BigDecimal oldPrice = priceListItems
            .findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
                list.getId(), request.itemId(), request.uomId(), minQty)
            .filter(p -> p.getValidTo() == null)
            .map(PriceListItem::getPrice)
            .orElse(null);
        requireApproval(changePct(oldPrice, request.price()), request.approverId());

        PriceListItem newRow = applyPrice(list, request.itemId(), request.uomId(), minQty,
            request.price(), request.effectiveFrom(), request.reason());
        return toDto(newRow, item, uom);
    }

    @Override
    @Transactional
    @Auditable(action = "DISCONTINUE_PRICE", entityType = "PriceList")
    public void discontinuePriceByPriceListUid(String priceListUid, DiscontinuePriceRequestDto request) {
        PriceList list = requirePriceListByUid(priceListUid);
        requireActive(list);
        BigDecimal minQty = normalizeMinQty(request.minQty());
        LocalDate eff = request.effectiveFrom();
        PriceListItem open = priceListItems
            .findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
                list.getId(), request.itemId(), request.uomId(), minQty)
            .filter(p -> p.getValidTo() == null)
            .orElseThrow(() -> new IllegalArgumentException("No active price to discontinue"));
        if (!eff.isAfter(open.getValidFrom())) {
            throw new IllegalArgumentException(
                "Discontinuation must take effect after the price's start date (" + open.getValidFrom() + ")");
        }
        open.closeOn(eff);
        priceChangeLog.save(new PriceChangeLog(open.getId(), open.getPrice(), null, eff,
            context.userId(), request.reason()));
        events.publish("ItemPriceDiscontinued.v1", "Item", String.valueOf(request.itemId()),
            Map.of("itemId", request.itemId(), "priceListId", list.getId(),
                "uomId", request.uomId(), "minQty", minQty));
    }

    // ---- bulk --------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "COPY_PRICES", entityType = "PriceList")
    public int copyPricesIntoPriceListUid(String priceListUid, CopyPricesRequestDto request) {
        PriceList target = requirePriceListByUid(priceListUid);
        requireActive(target);
        PriceList source = requirePriceListByUid(request.sourcePriceListUid());
        if (!target.getCurrencyCode().equals(source.getCurrencyCode())) {
            throw new IllegalArgumentException("Source and target price lists must share the same currency");
        }
        requireWithinWindow(target, request.effectiveFrom());
        BigDecimal pct = request.adjustPct() != null ? request.adjustPct() : BigDecimal.ZERO;
        requireApproval(pct.abs(), request.approverId());

        List<PriceListItem> sourceRows = priceListItems.findEffective(source.getId(), LocalDate.now());
        for (PriceListItem r : sourceRows) {
            applyPrice(target, r.getItemId(), r.getUomId(), r.getMinQty(),
                shift(r.getPrice(), pct), request.effectiveFrom(), request.reason());
        }
        return sourceRows.size();
    }

    @Override
    @Transactional
    @Auditable(action = "ADJUST_PRICES", entityType = "PriceList")
    public int adjustPricesByPriceListUid(String priceListUid, AdjustPricesRequestDto request) {
        PriceList list = requirePriceListByUid(priceListUid);
        requireActive(list);
        requireWithinWindow(list, request.effectiveFrom());
        requireApproval(request.adjustPct().abs(), request.approverId());

        List<PriceListItem> current = priceListItems.findEffective(list.getId(), LocalDate.now());
        for (PriceListItem r : current) {
            applyPrice(list, r.getItemId(), r.getUomId(), r.getMinQty(),
                shift(r.getPrice(), request.adjustPct()), request.effectiveFrom(), request.reason());
        }
        return current.size();
    }

    // ---- audit -------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PriceChangeLogDto> priceHistoryByItemUid(String itemUid) {
        Item item = requireItemByUid(itemUid);
        return priceChangeLog.findByItemId(item.getId()).stream()
            .map(PriceChangeLogDto::from)
            .toList();
    }

    // ---- internals ---------------------------------------------------------

    /** Close-and-open one (item, UoM, tier) price row + log + event. Caller owns validation/approval. */
    private PriceListItem applyPrice(PriceList list, Long itemId, Long uomId, BigDecimal minQty,
                                     BigDecimal newPrice, LocalDate eff, String reason) {
        Long listId = list.getId();
        var latest = priceListItems
            .findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(listId, itemId, uomId, minQty);
        BigDecimal oldPrice = null;
        if (latest.isPresent()) {
            PriceListItem prior = latest.get();
            if (!eff.isAfter(prior.getValidFrom())) {
                throw new IllegalArgumentException(
                    "New price must take effect after the current price's start date (" + prior.getValidFrom() + ")");
            }
            if (prior.getValidTo() == null) {
                oldPrice = prior.getPrice();
                prior.closeOn(eff);
            }
        }
        PriceListItem newRow = priceListItems.save(
            new PriceListItem(listId, itemId, uomId, minQty, newPrice, eff));
        priceChangeLog.save(new PriceChangeLog(newRow.getId(), oldPrice, newPrice, eff, context.userId(), reason));
        events.publish("ItemPriceChanged.v1", "Item", String.valueOf(itemId),
            Map.of("itemId", itemId, "priceListId", listId, "uomId", uomId,
                "minQty", minQty, "price", newPrice));
        return newRow;
    }

    private void requireApproval(BigDecimal changePct, Long approverId) {
        BigDecimal threshold = settings.getDecimal(SettingKey.PRICING_CHANGE_APPROVAL_PCT);
        if (threshold.signum() <= 0) {
            return;                                   // gate disabled
        }
        if (changePct == null || changePct.compareTo(threshold) <= 0) {
            return;                                   // below threshold (or no comparable old price)
        }
        Long actorId = context.userId();
        if (approverId == null) {
            throw new IllegalArgumentException(
                "Price change above " + threshold + "% requires an authoriser holding " + PRICE_APPROVE_PERMISSION);
        }
        if (Objects.equals(approverId, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own price change");
        }
        if (!permissions.resolve(approverId, context.companyId(), null).contains(PRICE_APPROVE_PERMISSION)) {
            throw new AccessDeniedException(
                "Authoriser " + approverId + " does not hold " + PRICE_APPROVE_PERMISSION);
        }
    }

    private static BigDecimal shift(BigDecimal price, BigDecimal pct) {
        if (pct == null || pct.signum() == 0) {
            return price;
        }
        BigDecimal factor = BigDecimal.ONE.add(pct.divide(HUNDRED, 6, RoundingMode.HALF_UP));
        return price.multiply(factor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal changePct(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || oldPrice.signum() == 0) {
            return null;
        }
        return newPrice.subtract(oldPrice).abs().multiply(HUNDRED).divide(oldPrice, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeMinQty(BigDecimal minQty) {
        return minQty != null ? minQty : BigDecimal.ZERO;
    }

    private void requireActive(PriceList list) {
        if (list.getStatus() != ItemStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot change prices on a " + list.getStatus() + " price list");
        }
    }

    private static void requireWithinWindow(PriceList list, LocalDate eff) {
        if (eff.isBefore(list.getValidFrom())
            || (list.getValidTo() != null && eff.isAfter(list.getValidTo()))) {
            throw new IllegalArgumentException(
                "Effective date " + eff + " is outside the price list's validity window");
        }
    }

    private static void requireValidWindow(LocalDate from, LocalDate to) {
        if (to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("valid_to cannot be before valid_from");
        }
    }

    private List<PriceListItemDto> enrich(List<PriceListItem> rows) {
        Set<Long> itemIds = rows.stream().map(PriceListItem::getItemId).collect(Collectors.toSet());
        Set<Long> uomIds = rows.stream().map(PriceListItem::getUomId).collect(Collectors.toSet());
        Map<Long, Item> itemMap = items.findAllById(itemIds).stream()
            .collect(Collectors.toMap(Item::getId, i -> i));
        Map<Long, Uom> uomMap = uoms.findAllById(uomIds).stream()
            .collect(Collectors.toMap(Uom::getId, u -> u));
        return rows.stream()
            .map(r -> toDto(r, itemMap.get(r.getItemId()), uomMap.get(r.getUomId())))
            .toList();
    }

    private PriceListItemDto toDto(PriceListItem r, Item item, Uom uom) {
        return PriceListItemDto.of(r,
            item != null ? item.getCode() : null,
            item != null ? item.getName() : null,
            uom != null ? uom.getCode() : null);
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

    private Uom requireUomById(Long uomId) {
        return uoms.findById(uomId)
            .orElseThrow(() -> new NoSuchElementException("UoM not found: " + uomId));
    }
}
