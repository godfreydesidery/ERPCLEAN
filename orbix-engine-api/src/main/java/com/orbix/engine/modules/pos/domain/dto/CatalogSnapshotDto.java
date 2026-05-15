package com.orbix.engine.modules.pos.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Catalog snapshot for a till's offline catalogue (F5.4). Returns the active
 * items the cashier can scan, their barcodes, the till's price list, and
 * each item's per-branch on-hand qty so the till can warn on oversell while
 * disconnected.
 */
public record CatalogSnapshotDto(
    Instant snapshotAt,
    Long branchId,
    Long priceListId,
    List<ItemSnapshot> items
) {
    public record ItemSnapshot(
        Long itemId,
        String code,
        String name,
        String type,
        Long uomId,
        Long vatGroupId,
        BigDecimal vatRate,
        boolean weighed,
        boolean batchTracked,
        BigDecimal minSellPrice,
        BigDecimal price,
        BigDecimal qtyOnHand,
        List<BarcodeSnapshot> barcodes
    ) {}

    public record BarcodeSnapshot(
        String barcode,
        String barcodeType,
        Long packUomId,
        BigDecimal packQty
    ) {}
}
