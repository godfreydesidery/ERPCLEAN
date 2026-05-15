package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceChangeLogDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;
import com.orbix.engine.modules.catalog.service.PriceListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Price-list maintenance + price-change audit (F1.5). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PriceListController {

    private final PriceListService service;

    @GetMapping("/price-lists")
    public List<PriceListDto> listPriceLists() {
        return service.listPriceLists();
    }

    @GetMapping("/price-lists/{id}")
    public PriceListDto getPriceList(@PathVariable Long id) {
        return service.getPriceList(id);
    }

    @PostMapping("/price-lists")
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<PriceListDto> createPriceList(
            @Valid @RequestBody CreatePriceListRequestDto request) {
        PriceListDto list = service.createPriceList(request);
        return ResponseEntity.created(URI.create("/api/v1/price-lists/" + list.id())).body(list);
    }

    @PatchMapping("/price-lists/{id}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public PriceListDto updatePriceList(@PathVariable Long id,
                                        @Valid @RequestBody UpdatePriceListRequestDto request) {
        return service.updatePriceList(id, request);
    }

    @PostMapping("/price-lists/{id}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archivePriceList(@PathVariable Long id) {
        service.archivePriceList(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/price-lists/{id}/items")
    public List<PriceListItemDto> listPrices(@PathVariable Long id) {
        return service.listPrices(id);
    }

    @PutMapping("/price-lists/{id}/items")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public PriceListItemDto setPrice(@PathVariable Long id,
                                     @Valid @RequestBody SetPriceRequestDto request) {
        return service.setPrice(id, request);
    }

    @GetMapping("/items/{itemId}/price-changes")
    public List<PriceChangeLogDto> priceHistory(@PathVariable Long itemId) {
        return service.priceHistoryForItem(itemId);
    }
}
