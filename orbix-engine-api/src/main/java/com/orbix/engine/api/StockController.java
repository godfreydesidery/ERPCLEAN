package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Read access to the stock ledger (F2.2). Moves are posted by the modules that
 * own the causing document — there is no direct POST here.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class StockController {

    private final StockMoveService service;

    @GetMapping("/stock-moves")
    public PageDto<StockMoveDto> listMoves(
            @RequestParam(required = false) Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.listMoves(branchId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "at")));
    }

    @GetMapping("/balances")
    public List<ItemBranchBalanceDto> listBalances(@RequestParam Long branchId) {
        return service.listBalances(branchId);
    }

    @GetMapping("/stock-card")
    public PageDto<StockMoveDto> stockCard(
            @RequestParam Long itemId,
            @RequestParam Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return service.stockCard(itemId, branchId, PageRequest.of(page, size));
    }
}
