package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import com.orbix.engine.modules.stock.repository.StockBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ItemServiceImpl implements ItemService {

    private final ItemRepository repo;
    private final ItemBarcodeRepository barcodes;
    private final StockBatchRepository stockBatches;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Item")
    public ItemResponseDto create(CreateItemRequestDto request) {
        Long companyId = context.companyId();
        repo.findByCompanyAndCode(companyId, request.code()).ifPresent(existing -> {
            throw new IllegalArgumentException("Item code already exists: " + request.code());
        });
        Item item = new Item(
            companyId,
            request.code(),
            request.name(),
            request.type(),
            request.itemGroupId(),
            request.uomId(),
            request.vatGroupId(),
            context.userId()
        );
        String shortName = request.shortName();
        if (shortName != null) {
            String trimmed = shortName.trim();
            item.setShortName(trimmed.isEmpty() ? null : trimmed);
        }
        Item saved = repo.save(item);
        events.publish(
            "ItemCreated.v1",
            "Item",
            String.valueOf(saved.getId()),
            Map.of("itemId", saved.getId(), "code", saved.getCode(), "companyId", companyId)
        );
        return ItemResponseDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<ItemResponseDto> listItems(ItemStatus status, Pageable pageable) {
        Long companyId = context.companyId();
        var page = status == null
            ? repo.findByCompanyId(companyId, pageable)
            : repo.findByCompanyIdAndStatus(companyId, status, pageable);
        return PageDto.of(page, ItemResponseDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public ItemResponseDto getItem(Long itemId) {
        return ItemResponseDto.from(requireItem(itemId));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Item")
    public ItemResponseDto updateItem(Long itemId, UpdateItemRequestDto request) {
        Item item = requireItem(itemId);
        Long actorId = context.userId();

        item.update(request.name(), request.shortName(), request.type(), request.itemGroupId(),
            request.uomId(), request.vatGroupId(), request.tracked(), request.minSellPrice(),
            actorId);
        events.publish("ItemUpdated.v1", "Item", String.valueOf(item.getId()),
            Map.of("itemId", item.getId()));

        applyWeighing(item, request, actorId);
        applyBatchTracking(item, request.batchTracked(), actorId);

        return ItemResponseDto.from(item);
    }

    /** Phase-1.1 weighed-item rules: unit ⟺ weighed, and weighed items need a PLU / embedded-weight barcode. */
    private void applyWeighing(Item item, UpdateItemRequestDto request, Long actorId) {
        WeighingUnit unit = request.weighingUnit();
        boolean weighed = request.weighed();
        if (weighed == (unit == null)) {
            throw new IllegalArgumentException(
                "weighingUnit must be set if and only if the item is weighed");
        }
        if (weighed && !hasWeighedCapableBarcode(item.getId())) {
            throw new IllegalArgumentException(
                "A weighed item needs at least one PLU or EMBEDDED_WEIGHT barcode");
        }

        boolean changed = item.isWeighed() != weighed || item.getWeighingUnit() != unit;
        item.applyWeighing(weighed, unit, actorId);
        if (changed) {
            events.publish("ItemWeighingChanged.v1", "Item", String.valueOf(item.getId()),
                Map.of("itemId", item.getId(), "weighed", weighed));
        }
    }

    private void applyBatchTracking(Item item, boolean batchTracked, Long actorId) {
        boolean was = item.isBatchTracked();
        if (was == batchTracked) {
            return;
        }
        if (was && !batchTracked && hasActiveBatches(item.getId())) {
            throw new IllegalArgumentException(
                "Cannot disable batch tracking while item " + item.getId() + " has active stock batches");
        }
        item.applyBatchTracking(batchTracked, actorId);
        String type = batchTracked ? "ItemBatchTrackingEnabled.v1" : "ItemBatchTrackingDisabled.v1";
        events.publish(type, "Item", String.valueOf(item.getId()), Map.of("itemId", item.getId()));
    }

    private boolean hasActiveBatches(Long itemId) {
        return stockBatches.existsByItemIdAndStatus(itemId, StockBatchStatus.ACTIVE);
    }

    private boolean hasWeighedCapableBarcode(Long itemId) {
        return barcodes.findByItemId(itemId).stream()
            .map(ItemBarcode::getBarcodeType)
            .anyMatch(t -> t != null && t.isWeighedCapable());
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "Item")
    public void archiveItem(Long itemId) {
        Item item = requireItem(itemId);
        if (item.getStatus() == ItemStatus.ARCHIVED) {
            throw new IllegalArgumentException("Item is already archived: " + itemId);
        }
        if (item.isBatchTracked() && hasActiveBatches(itemId)) {
            throw new IllegalArgumentException(
                "Cannot archive item " + itemId + " while it has active stock batches");
        }
        item.archive(context.userId());
        events.publish("ItemArchived.v1", "Item", String.valueOf(item.getId()),
            Map.of("itemId", item.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Item")
    public void activateItem(Long itemId) {
        Item item = requireItem(itemId);
        if (item.getStatus() == ItemStatus.ACTIVE) {
            throw new IllegalArgumentException("Item is already active: " + itemId);
        }
        item.activate(context.userId());
        events.publish("ItemActivated.v1", "Item", String.valueOf(item.getId()),
            Map.of("itemId", item.getId()));
    }

    private Item requireItem(Long itemId) {
        Item item = repo.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }
}
