package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemBarcodeRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemBarcodeDto;

import java.util.List;

/** Barcode management for an item (F1.4). Barcodes are globally unique. */
public interface ItemBarcodeService {

    List<ItemBarcodeDto> listForItem(Long itemId);

    ItemBarcodeDto addBarcode(Long itemId, CreateItemBarcodeRequestDto request);

    void deleteBarcode(Long barcodeId);
}
