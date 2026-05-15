package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;

import java.math.BigDecimal;

/**
 * Result of a barcode lookup at the till (F5.8). For plain symbologies
 * ({@code UPC} / {@code EAN13} / {@code EAN8} / {@code PLU}) {@code qty}
 * is the barcode's {@code packQty} (defaults to 1); for an
 * {@code EMBEDDED_WEIGHT} scan it is the weight decoded from bytes 8..12
 * of the EAN-13, expressed in the item's {@link WeighingUnit}.
 */
public record ResolvedBarcodeDto(
    Long itemId,
    String itemCode,
    String itemName,
    Long uomId,
    Long vatGroupId,
    boolean weighed,
    boolean batchTracked,
    WeighingUnit weighingUnit,
    BigDecimal minSellPrice,
    BigDecimal qty,
    BarcodeType barcodeType
) {}
