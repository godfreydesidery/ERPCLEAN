package com.orbix.engine.api;

import com.orbix.engine.modules.stock.domain.dto.RecallStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import com.orbix.engine.modules.stock.service.StockBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Stock batches + FEFO admin views (F2.4). Gated by {@code STOCK.BATCH}. */
@RestController
@RequestMapping("/api/v1/stock-batches")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STOCK.BATCH')")
public class StockBatchController {

    private final StockBatchService service;

    @GetMapping
    public List<StockBatchDto> listBatches(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) StockBatchStatus status) {
        return service.listBatches(branchId, itemId, status);
    }

    @GetMapping("/expiring-soon")
    public List<StockBatchDto> expiringSoon(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "30") int daysAhead) {
        return service.listExpiringSoon(branchId, daysAhead);
    }

    @GetMapping("/{id}")
    public StockBatchDto getBatch(@PathVariable Long id) {
        return service.getBatch(id);
    }

    @PostMapping("/{id}/recall")
    public StockBatchDto recallBatch(@PathVariable Long id,
                                     @Valid @RequestBody RecallStockBatchRequestDto request) {
        return service.recallBatch(id, request);
    }
}
