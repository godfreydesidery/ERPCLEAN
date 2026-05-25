package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.AdjustPricesRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.CopyPricesRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.DiscontinuePriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceChangeLogDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;
import com.orbix.engine.modules.catalog.service.PriceListService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Price-list maintenance + price-change audit (F1.5). Uses dedicated
 * {@code PRICE_LIST.*} / {@code PRICE.*} permissions.
 */
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

    @GetMapping("/price-lists/code/{code}")
    public PriceListDto getPriceListByCode(@PathVariable String code) {
        return service.getPriceListByCode(code);
    }

    @PostMapping("/price-lists")
    @PreAuthorize("hasAuthority('PRICE_LIST.CREATE')")
    public ResponseEntity<PriceListDto> createPriceList(
            @Valid @RequestBody CreatePriceListRequestDto request) {
        PriceListDto list = service.createPriceList(request);
        return ResponseEntity.created(URI.create("/api/v1/price-lists/uid/" + list.uid())).body(list);
    }

    @PatchMapping("/price-lists/uid/{uid}")
    @PreAuthorize("hasAuthority('PRICE_LIST.UPDATE')")
    public PriceListDto updatePriceList(@PathVariable @ValidUlid String uid,
                                        @Valid @RequestBody UpdatePriceListRequestDto request) {
        return service.updatePriceListByUid(uid, request);
    }

    @PostMapping("/price-lists/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('PRICE_LIST.ARCHIVE')")
    public ResponseEntity<Void> archivePriceList(@PathVariable @ValidUlid String uid) {
        service.archivePriceListByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/price-lists/uid/{uid}/activate")
    @PreAuthorize("hasAuthority('PRICE_LIST.ARCHIVE')")
    public ResponseEntity<Void> activatePriceList(@PathVariable @ValidUlid String uid) {
        service.activatePriceListByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/price-lists/uid/{uid}/items")
    public List<PriceListItemDto> listPrices(
            @PathVariable @ValidUlid String uid,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.listPricesByPriceListUid(uid, asOf);
    }

    @GetMapping("/price-lists/uid/{uid}/items/resolve")
    public PriceListItemDto resolvePrice(
            @PathVariable @ValidUlid String uid,
            @RequestParam Long itemId,
            @RequestParam Long uomId,
            @RequestParam(required = false) BigDecimal qty,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return service.resolvePrice(uid, itemId, uomId, qty, asOf);
    }

    @PutMapping("/price-lists/uid/{uid}/items")
    @PreAuthorize("hasAuthority('PRICE.SET')")
    public PriceListItemDto setPrice(@PathVariable @ValidUlid String uid,
                                     @Valid @RequestBody SetPriceRequestDto request) {
        return service.setPriceByPriceListUid(uid, request);
    }

    @DeleteMapping("/price-lists/uid/{uid}/items")
    @PreAuthorize("hasAuthority('PRICE.SET')")
    public ResponseEntity<Void> discontinuePrice(@PathVariable @ValidUlid String uid,
                                                 @Valid @RequestBody DiscontinuePriceRequestDto request) {
        service.discontinuePriceByPriceListUid(uid, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/price-lists/uid/{uid}/items/copy-from")
    @PreAuthorize("hasAuthority('PRICE.SET')")
    public Map<String, Integer> copyPrices(@PathVariable @ValidUlid String uid,
                                           @Valid @RequestBody CopyPricesRequestDto request) {
        return Map.of("rowsWritten", service.copyPricesIntoPriceListUid(uid, request));
    }

    @PostMapping("/price-lists/uid/{uid}/items/adjust")
    @PreAuthorize("hasAuthority('PRICE.SET')")
    public Map<String, Integer> adjustPrices(@PathVariable @ValidUlid String uid,
                                             @Valid @RequestBody AdjustPricesRequestDto request) {
        return Map.of("rowsWritten", service.adjustPricesByPriceListUid(uid, request));
    }

    @GetMapping("/items/uid/{itemUid}/price-changes")
    public List<PriceChangeLogDto> priceHistory(@PathVariable @ValidUlid String itemUid) {
        return service.priceHistoryByItemUid(itemUid);
    }
}
