package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.stock.domain.dto.CreateStockTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.ReceiveTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockTransferDto;
import com.orbix.engine.modules.stock.service.StockTransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Inter-branch stock transfers (F2.3). Gated by {@code STOCK.TRANSFER}.
 * Transfers are addressed externally by their {@code uid} (a ULID) via the
 * literal {@code /uid/{uid}} segment; the numeric {@code id} stays in the body.
 */
@RestController
@RequestMapping("/api/v1/stock-transfers")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('STOCK.TRANSFER')")
public class StockTransferController {

    private final StockTransferService service;

    @GetMapping
    public List<StockTransferDto> listTransfers() {
        return service.listTransfers();
    }

    @GetMapping("/uid/{uid}")
    public StockTransferDto getTransfer(@PathVariable @ValidUlid String uid) {
        return service.getTransfer(uid);
    }

    @PostMapping
    public ResponseEntity<StockTransferDto> createTransfer(
            @Valid @RequestBody CreateStockTransferRequestDto request) {
        StockTransferDto transfer = service.createTransfer(request);
        return ResponseEntity.created(URI.create("/api/v1/stock-transfers/uid/" + transfer.uid()))
            .body(transfer);
    }

    @PostMapping("/uid/{uid}/issue")
    public StockTransferDto issueTransfer(@PathVariable @ValidUlid String uid) {
        return service.issueTransfer(uid);
    }

    @PutMapping("/uid/{uid}/receive")
    public StockTransferDto receiveTransfer(@PathVariable @ValidUlid String uid,
                                            @Valid @RequestBody ReceiveTransferRequestDto request) {
        return service.receiveTransfer(uid, request);
    }

    @PostMapping("/uid/{uid}/close")
    public StockTransferDto closeTransfer(@PathVariable @ValidUlid String uid) {
        return service.closeTransfer(uid);
    }
}
