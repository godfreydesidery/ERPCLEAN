package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemBarcodeRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemBarcodeDto;

import java.util.List;

/** Barcode management for an item (F1.4). Barcodes are globally unique. */
public interface ItemBarcodeService {

    List<ItemBarcodeDto> listForItemByUid(String itemUid);

    ItemBarcodeDto addBarcodeByItemUid(String itemUid, CreateItemBarcodeRequestDto request);

    void deleteBarcodeByUid(String uid);
}
