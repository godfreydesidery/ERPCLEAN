package com.orbix.engine.api;

import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.ItemMovementRowDto;
import com.orbix.engine.modules.stock.service.StockReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Stock-side reporting (F8.1 / US-RPT-004..006). Reuses existing
 * {@code STOCK.READ} permissions — every storekeeper can pull these.
 *
 * <p>Stock-card endpoint already lives on {@code StockController}
 * ({@code GET /api/v1/stock-card}); this controller covers the two
 * report-style endpoints that don't fit the page-by-id shape.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class StockReportController {

    private final StockReportService service;

    /** US-RPT-006 — items in negative on-hand for follow-up. */
    @GetMapping("/stock-negative")
    public List<ItemBranchBalanceDto> negativeOnHand(@RequestParam(required = false) Long branchId) {
        return service.negativeOnHand(branchId);
    }

    /**
     * US-RPT-005 — fast movers (top-N by total moved qty). Default move-type
     * set is just {@code SALE}; pass {@code moveTypes=SALE,PROD_CONSUME} etc.
     * to broaden the throughput view. Date range defaults to the last 30 days
     * if {@code from} / {@code to} are omitted.
     */
    @GetMapping("/stock-fast-movers")
    public List<ItemMovementRowDto> fastMovers(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> moveTypes,
            @RequestParam(defaultValue = "20") int limit) {
        return service.fastMovers(branchId, from, to, moveTypes, limit);
    }

    /** US-RPT-005 — slow movers (bottom-N by total moved qty; zero-movers included). */
    @GetMapping("/stock-slow-movers")
    public List<ItemMovementRowDto> slowMovers(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) List<String> moveTypes,
            @RequestParam(defaultValue = "20") int limit) {
        return service.slowMovers(branchId, from, to, moveTypes, limit);
    }
}
