package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncServiceImpl.class);

    private final PosSaleService posSaleService;
    private final ItemRepository items;
    private final ItemBarcodeRepository barcodes;
    private final VatGroupRepository vatGroups;
    private final PriceListItemRepository priceListItems;
    private final ItemBranchBalanceRepository balances;
    private final RequestContext context;

    /**
     * Deliberately NOT @Transactional — each PosSaleService.post call runs in
     * its own REQUIRED transaction, so one failing sale doesn't roll back the
     * batch. The till re-pushes any rejected items after the operator clears them.
     */
    @Override
    public SyncPushResultDto pushBatch(SyncPushRequestDto request) {
        List<SyncPushResultDto.Item> results = new ArrayList<>(request.sales().size());
        int accepted = 0;
        int rejected = 0;
        for (PostPosSaleRequestDto sale : request.sales()) {
            try {
                PosSaleDto posted = posSaleService.post(sale);
                results.add(SyncPushResultDto.Item.accepted(sale.clientOpId(), posted.id()));
                accepted++;
            } catch (RuntimeException ex) {
                log.warn("Sync push item rejected: clientOpId={} reason={}",
                    sale.clientOpId(), ex.getMessage());
                results.add(SyncPushResultDto.Item.rejected(sale.clientOpId(), ex.getMessage()));
                rejected++;
            }
        }
        return new SyncPushResultDto(accepted, rejected, results);
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogSnapshotDto catalogSnapshot(Long branchId, Long priceListId) {
        Long companyId = context.companyId();
        List<Item> activeItems = items.findByCompanyIdAndStatusOrderByIdAsc(companyId, ItemStatus.ACTIVE);

        Map<Long, VatGroup> vatById = new HashMap<>();
        vatGroups.findAll().forEach(v -> vatById.put(v.getId(), v));

        Map<Long, PriceListItem> priceByItem = new HashMap<>();
        priceListItems.findByPriceListIdAndValidToIsNull(priceListId)
            .forEach(p -> priceByItem.put(p.getItemId(), p));

        Map<Long, BigDecimal> onHandByItem = new HashMap<>();
        for (ItemBranchBalance b : balances.findByBranchId(branchId)) {
            onHandByItem.put(b.getItemId(), b.getQtyOnHand());
        }

        List<CatalogSnapshotDto.ItemSnapshot> snapshots = new ArrayList<>(activeItems.size());
        for (Item item : activeItems) {
            VatGroup vat = vatById.get(item.getVatGroupId());
            PriceListItem priceRow = priceByItem.get(item.getId());
            BigDecimal price = priceRow != null ? priceRow.getPrice() : BigDecimal.ZERO;
            BigDecimal qty = onHandByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            List<CatalogSnapshotDto.BarcodeSnapshot> barcodeSnapshots = new ArrayList<>();
            for (ItemBarcode bc : barcodes.findByItemId(item.getId())) {
                barcodeSnapshots.add(new CatalogSnapshotDto.BarcodeSnapshot(
                    bc.getBarcode(),
                    bc.getBarcodeType() != null ? bc.getBarcodeType().name() : null,
                    bc.getPackUomId(),
                    bc.getPackQty()
                ));
            }
            snapshots.add(new CatalogSnapshotDto.ItemSnapshot(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getType().name(),
                item.getUomId(),
                item.getVatGroupId(),
                vat != null ? vat.getRate() : BigDecimal.ZERO,
                item.isWeighed(),
                item.isBatchTracked(),
                item.getMinSellPrice(),
                price,
                qty,
                barcodeSnapshots
            ));
        }
        return new CatalogSnapshotDto(Instant.now(), branchId, priceListId, snapshots);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSnapshotDto balanceSnapshot(Long branchId) {
        List<BalanceSnapshotDto.Row> rows = balances.findByBranchId(branchId).stream()
            .map(b -> new BalanceSnapshotDto.Row(b.getItemId(), b.getQtyOnHand()))
            .toList();
        return new BalanceSnapshotDto(Instant.now(), branchId, rows);
    }
}
