package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemBarcodeRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemBarcodeDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ItemBarcodeServiceImpl implements ItemBarcodeService {

    private final ItemBarcodeRepository barcodes;
    private final ItemRepository items;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<ItemBarcodeDto> listForItem(Long itemId) {
        requireItem(itemId);
        return barcodes.findByItemId(itemId).stream()
            .sorted(Comparator.comparing(ItemBarcode::getBarcode))
            .map(ItemBarcodeDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "ADD_BARCODE", entityType = "Item")
    public ItemBarcodeDto addBarcode(Long itemId, CreateItemBarcodeRequestDto request) {
        Item item = requireItem(itemId);
        String code = request.barcode().trim();
        validateBarcodeFormat(code, request.barcodeType(), item);
        if (barcodes.existsByBarcode(code)) {
            throw new IllegalArgumentException("Barcode already in use: " + code);
        }
        ItemBarcode saved = barcodes.save(new ItemBarcode(
            itemId, code, request.barcodeType(), request.packUomId(), request.packQty()));
        events.publish("BarcodeAdded.v1", "Item", String.valueOf(itemId),
            Map.of("itemId", itemId, "barcodeId", saved.getId(), "barcode", code));
        return ItemBarcodeDto.from(saved);
    }

    @Override
    @Transactional
    @Auditable(action = "REMOVE_BARCODE", entityType = "Item")
    public void deleteBarcode(Long barcodeId) {
        ItemBarcode barcode = barcodes.findById(barcodeId)
            .orElseThrow(() -> new NoSuchElementException("Barcode not found: " + barcodeId));
        requireItem(barcode.getItemId());
        barcodes.delete(barcode);
    }

    private Item requireItem(Long itemId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!item.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    /**
     * Symbology-specific shape checks. {@code EMBEDDED_WEIGHT} stores the
     * 7-char EAN-13 prefix ({@code '2'} + 6-digit PLU); the till's barcode
     * resolver (F5.8) matches scanned codes against that prefix and decodes
     * the trailing 5 weight digits.
     */
    private static void validateBarcodeFormat(String code, BarcodeType type, Item item) {
        switch (type) {
            case EMBEDDED_WEIGHT -> {
                if (!item.isWeighed()) {
                    throw new IllegalArgumentException(
                        "EMBEDDED_WEIGHT barcode requires a weighed item (item "
                            + item.getCode() + " is not weighed)");
                }
                if (code.length() != 7 || code.charAt(0) != '2' || !isAllDigits(code)) {
                    throw new IllegalArgumentException(
                        "EMBEDDED_WEIGHT barcode must be 7 digits with leading '2': got " + code);
                }
            }
            case EAN13 -> requireDigitsOfLength(code, 13, "EAN13");
            case EAN8 -> requireDigitsOfLength(code, 8, "EAN8");
            case UPC -> requireDigitsOfLength(code, 12, "UPC");
            default -> {
                // PLU / EMBEDDED_PRICE: no strict format check at MVP.
            }
        }
    }

    private static void requireDigitsOfLength(String code, int length, String label) {
        if (code.length() != length || !isAllDigits(code)) {
            throw new IllegalArgumentException(
                label + " barcode must be " + length + " digits: got " + code);
        }
    }

    private static boolean isAllDigits(String code) {
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
