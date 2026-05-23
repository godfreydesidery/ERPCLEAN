package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.sales.domain.dto.CreateSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.domain.dto.SalesInvoiceDto;
import com.orbix.engine.modules.sales.domain.dto.VoidSalesInvoiceRequestDto;
import com.orbix.engine.modules.sales.service.SalesInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Back-office sales invoices (F4.2). Gated by {@code SALES.MANAGE_INVOICE}. */
@RestController
@RequestMapping("/api/v1/sales-invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SALES.MANAGE_INVOICE')")
public class SalesInvoiceController {

    private final SalesInvoiceService service;

    @GetMapping
    public PageDto<SalesInvoiceDto> list(@RequestParam(required = false) Long branchId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public SalesInvoiceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SalesInvoiceDto> create(
            @Valid @RequestBody CreateSalesInvoiceRequestDto request) {
        SalesInvoiceDto invoice = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-invoices/" + invoice.id()))
            .body(invoice);
    }

    @PostMapping("/{id}/post")
    public SalesInvoiceDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/void")
    public SalesInvoiceDto voidInvoice(@PathVariable Long id,
                                       @Valid @RequestBody VoidSalesInvoiceRequestDto request) {
        return service.voidInvoice(id, request);
    }

    @PostMapping("/{id}/cancel")
    public SalesInvoiceDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
