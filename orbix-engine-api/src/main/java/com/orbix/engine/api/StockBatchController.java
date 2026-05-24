package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.stock.domain.dto.RecallStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import com.orbix.engine.modules.stock.service.StockBatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Stock batches + FEFO admin views (F2.4). Gated by {@code STOCK.BATCH}.
 * Batches are addressed externally by their {@code uid} (a ULID) via the
 * literal {@code /uid/{uid}} segment; the numeric {@code id} stays in the body.
 */
@RestController
@RequestMapping("/api/v1/stock-batches")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('STOCK.BATCH')")
public class StockBatchController {

    private final StockBatchService service;

    @GetMapping
    public PageDto<StockBatchDto> listBatches(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long itemId,
            @RequestParam(required = false) StockBatchStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listBatches(branchId, itemId, status, PageRequest.of(page, size));
    }

    @GetMapping("/expiring-soon")
    public List<StockBatchDto> expiringSoon(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "30") int daysAhead) {
        return service.listExpiringSoon(branchId, daysAhead);
    }

    @GetMapping("/uid/{uid}")
    public StockBatchDto getBatch(@PathVariable @ValidUlid String uid) {
        return service.getBatchByUid(uid);
    }

    @PostMapping("/uid/{uid}/recall")
    public StockBatchDto recallBatch(@PathVariable @ValidUlid String uid,
                                     @Valid @RequestBody RecallStockBatchRequestDto request) {
        return service.recallBatchByUid(uid, request);
    }
}
