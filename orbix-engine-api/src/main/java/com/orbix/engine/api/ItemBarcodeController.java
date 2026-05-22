package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemBarcodeRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemBarcodeDto;
import com.orbix.engine.modules.catalog.service.ItemBarcodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Item barcode management (F1.4). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ItemBarcodeController {

    private final ItemBarcodeService service;

    @GetMapping("/items/{itemId}/barcodes")
    public List<ItemBarcodeDto> listBarcodes(@PathVariable Long itemId) {
        return service.listForItem(itemId);
    }

    @PostMapping("/items/{itemId}/barcodes")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<ItemBarcodeDto> addBarcode(
            @PathVariable Long itemId,
            @Valid @RequestBody CreateItemBarcodeRequestDto request) {
        ItemBarcodeDto barcode = service.addBarcode(itemId, request);
        return ResponseEntity.created(URI.create("/api/v1/barcodes/" + barcode.id())).body(barcode);
    }

    @DeleteMapping("/barcodes/{id}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<Void> deleteBarcode(@PathVariable Long id) {
        service.deleteBarcode(id);
        return ResponseEntity.noContent().build();
    }
}
