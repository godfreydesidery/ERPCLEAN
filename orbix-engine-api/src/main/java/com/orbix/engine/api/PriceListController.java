package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceChangeLogDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;
import com.orbix.engine.modules.catalog.service.PriceListService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Price-list maintenance + price-change audit (F1.5). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Validated
public class PriceListController {

    private final PriceListService service;

    @GetMapping("/price-lists")
    public List<PriceListDto> listPriceLists() {
        return service.listPriceLists();
    }

    @GetMapping("/price-lists/uid/{uid}")
    public PriceListDto getPriceList(@PathVariable @ValidUlid String uid) {
        return service.getPriceListByUid(uid);
    }

    @PostMapping("/price-lists")
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<PriceListDto> createPriceList(
            @Valid @RequestBody CreatePriceListRequestDto request) {
        PriceListDto list = service.createPriceList(request);
        return ResponseEntity.created(URI.create("/api/v1/price-lists/uid/" + list.uid())).body(list);
    }

    @PatchMapping("/price-lists/uid/{uid}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public PriceListDto updatePriceList(@PathVariable @ValidUlid String uid,
                                        @Valid @RequestBody UpdatePriceListRequestDto request) {
        return service.updatePriceListByUid(uid, request);
    }

    @PostMapping("/price-lists/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archivePriceList(@PathVariable @ValidUlid String uid) {
        service.archivePriceListByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/price-lists/uid/{uid}/items")
    public List<PriceListItemDto> listPrices(@PathVariable @ValidUlid String uid) {
        return service.listPricesByPriceListUid(uid);
    }

    @PutMapping("/price-lists/uid/{uid}/items")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public PriceListItemDto setPrice(@PathVariable @ValidUlid String uid,
                                     @Valid @RequestBody SetPriceRequestDto request) {
        return service.setPriceByPriceListUid(uid, request);
    }

    @GetMapping("/items/uid/{itemUid}/price-changes")
    public List<PriceChangeLogDto> priceHistory(@PathVariable @ValidUlid String itemUid) {
        return service.priceHistoryByItemUid(itemUid);
    }
}
