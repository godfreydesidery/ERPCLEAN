package com.orbix.engine.api;

import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecordCountsRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockCountDto;
import com.orbix.engine.modules.stock.service.StockCountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Physical stock counts (F2.3). Gated by {@code STOCK.COUNT}. */
@RestController
@RequestMapping("/api/v1/stock-counts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STOCK.COUNT')")
public class StockCountController {

    private final StockCountService service;

    @GetMapping
    public List<StockCountDto> listCounts(@RequestParam(required = false) Long branchId) {
        return service.listCounts(branchId);
    }

    @GetMapping("/{id}")
    public StockCountDto getCount(@PathVariable Long id) {
        return service.getCount(id);
    }

    @PostMapping
    public ResponseEntity<StockCountDto> createCount(
            @Valid @RequestBody CreateStockCountRequestDto request) {
        StockCountDto count = service.createCount(request);
        return ResponseEntity.created(URI.create("/api/v1/stock-counts/" + count.id())).body(count);
    }

    @PostMapping("/{id}/start")
    public StockCountDto startCount(@PathVariable Long id) {
        return service.startCount(id);
    }

    @PutMapping("/{id}/counts")
    public StockCountDto recordCounts(@PathVariable Long id,
                                      @Valid @RequestBody RecordCountsRequestDto request) {
        return service.recordCounts(id, request);
    }

    @PostMapping("/{id}/close")
    public StockCountDto closeCount(@PathVariable Long id) {
        return service.closeCount(id);
    }

    @PostMapping("/{id}/post")
    public StockCountDto postCount(@PathVariable Long id) {
        return service.postCount(id);
    }
}
