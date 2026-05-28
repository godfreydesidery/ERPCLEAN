package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecordCountsRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockCountDto;
import com.orbix.engine.modules.stock.service.StockCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Physical stock counts (F2.3). Gated by {@code STOCK.COUNT}. Counts are
 * addressed externally by their {@code uid} (a ULID) via the literal
 * {@code /uid/{uid}} segment; the numeric {@code id} stays in the body.
 */
@RestController
@RequestMapping("/api/v1/stock-counts")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('STOCK.COUNT')")
public class StockCountController {

    private final StockCountService service;

    @GetMapping
    public List<StockCountDto> listCounts(@RequestParam(required = false) Long branchId) {
        return service.listCounts(branchId);
    }

    @GetMapping("/uid/{uid}")
    public StockCountDto getCount(@PathVariable @ValidUlid String uid) {
        return service.getCount(uid);
    }

    @PostMapping
    public ResponseEntity<StockCountDto> createCount(
            @Valid @RequestBody CreateStockCountRequestDto request) {
        StockCountDto count = service.createCount(request);
        return ResponseEntity.created(URI.create("/api/v1/stock-counts/uid/" + count.uid())).body(count);
    }

    @PostMapping("/uid/{uid}/start")
    public StockCountDto startCount(@PathVariable @ValidUlid String uid) {
        return service.startCount(uid);
    }

    @PutMapping("/uid/{uid}/counts")
    public StockCountDto recordCounts(@PathVariable @ValidUlid String uid,
                                      @Valid @RequestBody RecordCountsRequestDto request) {
        return service.recordCounts(uid, request);
    }

    @PostMapping("/uid/{uid}/close")
    public StockCountDto closeCount(@PathVariable @ValidUlid String uid) {
        return service.closeCount(uid);
    }

    @PostMapping("/uid/{uid}/post")
    public StockCountDto postCount(@PathVariable @ValidUlid String uid,
                                   @RequestBody(required = false) PostStockCountRequestDto request) {
        return service.postCount(uid, request);
    }
}
