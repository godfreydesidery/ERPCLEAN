package com.orbix.engine.api;

import com.orbix.engine.modules.stock.domain.dto.CreateStockTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.ReceiveTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockTransferDto;
import com.orbix.engine.modules.stock.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Inter-branch stock transfers (F2.3). Gated by {@code STOCK.TRANSFER}. */
@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STOCK.TRANSFER')")
public class StockTransferController {

    private final StockTransferService service;

    @GetMapping
    public List<StockTransferDto> listTransfers() {
        return service.listTransfers();
    }

    @GetMapping("/{id}")
    public StockTransferDto getTransfer(@PathVariable Long id) {
        return service.getTransfer(id);
    }

    @PostMapping
    public ResponseEntity<StockTransferDto> createTransfer(
            @Valid @RequestBody CreateStockTransferRequestDto request) {
        StockTransferDto transfer = service.createTransfer(request);
        return ResponseEntity.created(URI.create("/api/v1/stock-transfers/" + transfer.id()))
            .body(transfer);
    }

    @PostMapping("/{id}/issue")
    public StockTransferDto issueTransfer(@PathVariable Long id) {
        return service.issueTransfer(id);
    }

    @PutMapping("/{id}/receive")
    public StockTransferDto receiveTransfer(@PathVariable Long id,
                                            @Valid @RequestBody ReceiveTransferRequestDto request) {
        return service.receiveTransfer(id, request);
    }

    @PostMapping("/{id}/close")
    public StockTransferDto closeTransfer(@PathVariable Long id) {
        return service.closeTransfer(id);
    }
}
