package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemBarcodeRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemBarcodeDto;
import com.orbix.engine.modules.catalog.service.ItemBarcodeService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Item barcode management (F1.4). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class ItemBarcodeController {

    private final ItemBarcodeService service;

    @GetMapping("/items/uid/{itemUid}/barcodes")
    public List<ItemBarcodeDto> listBarcodes(@PathVariable @ValidUlid String itemUid) {
        return service.listForItemByUid(itemUid);
    }

    @PostMapping("/items/uid/{itemUid}/barcodes")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<ItemBarcodeDto> addBarcode(
            @PathVariable @ValidUlid String itemUid,
            @Valid @RequestBody CreateItemBarcodeRequestDto request) {
        ItemBarcodeDto barcode = service.addBarcodeByItemUid(itemUid, request);
        return ResponseEntity.created(URI.create("/api/v1/barcodes/uid/" + barcode.uid())).body(barcode);
    }

    @DeleteMapping("/barcodes/uid/{uid}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<Void> deleteBarcode(@PathVariable @ValidUlid String uid) {
        service.deleteBarcodeByUid(uid);
        return ResponseEntity.noContent().build();
    }
}
