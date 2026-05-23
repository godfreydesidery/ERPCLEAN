package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesReceiptRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesReceiptDto;
import com.orbix.engine.modules.sales.service.SalesReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Sales receipts + allocation (F4.3). Gated by {@code SALES.MANAGE_RECEIPT}. */
@RestController
@RequestMapping("/api/v1/sales-receipts")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SALES.MANAGE_RECEIPT')")
public class SalesReceiptController {

    private final SalesReceiptService service;

    @GetMapping
    public PageDto<SalesReceiptDto> list(@RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public SalesReceiptDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SalesReceiptDto> create(
            @Valid @RequestBody CreateSalesReceiptRequestDto request) {
        SalesReceiptDto receipt = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-receipts/" + receipt.id()))
            .body(receipt);
    }

    @PostMapping("/{id}/post")
    public SalesReceiptDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    public SalesReceiptDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
