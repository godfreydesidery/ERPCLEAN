package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.dto.ResolvedBarcodeDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BarcodeResolverServiceImpl implements BarcodeResolverService {

    private final ItemBarcodeRepository barcodes;
    private final ItemRepository items;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public ResolvedBarcodeDto resolve(String scannedCode) {
        if (scannedCode == null || scannedCode.isBlank()) {
            throw new IllegalArgumentException("scannedCode is required");
        }
        String code = scannedCode.trim();
        Long companyId = context.companyId();

        // Plain-symbology path: exact match wins, regardless of barcode_type.
        Optional<ItemBarcode> exact = barcodes.findByBarcode(code);
        if (exact.isPresent()) {
            Item item = requireItem(exact.get().getItemId(), companyId);
            return toDto(item, exact.get(), exact.get().getPackQty());
        }

        // Embedded-weight fallback: 13-digit code starting with 2, match the 7-char prefix.
        if (EmbeddedWeightDecoder.isCandidate(code)) {
            String prefix = EmbeddedWeightDecoder.prefix(code);
            ItemBarcode match = barcodes
                .findByBarcodeAndBarcodeType(prefix, BarcodeType.EMBEDDED_WEIGHT)
                .orElseThrow(() -> new NoSuchElementException(
                    "No EMBEDDED_WEIGHT barcode matches PLU " + prefix));
            BigDecimal qty = EmbeddedWeightDecoder.decodedWeight(code);
            if (qty.signum() <= 0) {
                throw new IllegalArgumentException(
                    "Embedded-weight scan decoded to zero qty: " + code);
            }
            Item item = requireItem(match.getItemId(), companyId);
            return toDto(item, match, qty);
        }

        throw new NoSuchElementException("Barcode not found: " + code);
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        if (item.getStatus() != ItemStatus.ACTIVE) {
            throw new IllegalArgumentException(
                "Item " + item.getCode() + " is " + item.getStatus() + " — not sellable at the till");
        }
        return item;
    }

    private static ResolvedBarcodeDto toDto(Item item, ItemBarcode barcode, BigDecimal qty) {
        return new ResolvedBarcodeDto(
            item.getId(),
            item.getCode(),
            item.getName(),
            item.getUomId(),
            item.getVatGroupId(),
            item.isWeighed(),
            item.isBatchTracked(),
            item.getWeighingUnit(),
            item.getMinSellPrice(),
            qty,
            barcode.getBarcodeType()
        );
    }
}
