package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;

import java.math.BigDecimal;

public record ItemBarcodeDto(
    Long id,
    String uid,
    Long itemId,
    String barcode,
    BarcodeType barcodeType,
    Long packUomId,
    BigDecimal packQty
) {
    public static ItemBarcodeDto from(ItemBarcode barcode) {
        return new ItemBarcodeDto(
            barcode.getId(),
            barcode.getUid(),
            barcode.getItemId(),
            barcode.getBarcode(),
            barcode.getBarcodeType(),
            barcode.getPackUomId(),
            barcode.getPackQty()
        );
    }
}
