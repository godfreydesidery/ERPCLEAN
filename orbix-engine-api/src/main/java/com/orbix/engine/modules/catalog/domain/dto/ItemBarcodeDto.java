package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;

import java.math.BigDecimal;

public record ItemBarcodeDto(
    Long id,
    Long itemId,
    String barcode,
    Long packUomId,
    BigDecimal packQty
) {
    public static ItemBarcodeDto from(ItemBarcode barcode) {
        return new ItemBarcodeDto(
            barcode.getId(),
            barcode.getItemId(),
            barcode.getBarcode(),
            barcode.getPackUomId(),
            barcode.getPackQty()
        );
    }
}
