package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.service.SalesInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Back-office sales invoices (F4.2). Gated by {@code SALES.MANAGE_INVOICE}.
 * Invoices are addressed externally by their {@code uid} (a ULID) via the
 * literal {@code /uid/{uid}} segment; the numeric {@code id} stays in the body.
 */
@RestController
@RequestMapping("/api/v1/sales-invoices")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('SALES.MANAGE_INVOICE')")
public class SalesInvoiceController {

    private final SalesInvoiceService service;

    @GetMapping
    public PageDto<SalesInvoiceDto> list(@RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    public SalesInvoiceDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<SalesInvoiceDto> create(
            @Valid @RequestBody CreateSalesInvoiceRequestDto request) {
        SalesInvoiceDto invoice = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-invoices/uid/" + invoice.uid()))
            .body(invoice);
    }

    @PostMapping("/uid/{uid}/post")
    public SalesInvoiceDto post(@PathVariable @ValidUlid String uid) {
        return service.post(uid);
    }

    @PostMapping("/uid/{uid}/void")
    public SalesInvoiceDto voidInvoice(@PathVariable @ValidUlid String uid,
                                       @Valid @RequestBody VoidSalesInvoiceRequestDto request) {
        return service.voidInvoice(uid, request);
    }

    @PostMapping("/uid/{uid}/cancel")
    public SalesInvoiceDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }
}
