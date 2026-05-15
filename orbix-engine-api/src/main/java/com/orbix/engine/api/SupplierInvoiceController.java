package com.orbix.engine.api;

import com.orbix.engine.modules.procurement.domain.dto.CreateSupplierInvoiceRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import com.orbix.engine.modules.procurement.service.SupplierInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Supplier invoice + 3-way match (F3.3). Gated by {@code PROCUREMENT.MANAGE_INVOICE}. */
@RestController
@RequestMapping("/api/v1/supplier-invoices")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_INVOICE')")
public class SupplierInvoiceController {

    private final SupplierInvoiceService service;

    @GetMapping
    public List<SupplierInvoiceDto> list(@RequestParam(required = false) Long branchId) {
        return service.list(branchId);
    }

    @GetMapping("/{id}")
    public SupplierInvoiceDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierInvoiceDto> create(
            @Valid @RequestBody CreateSupplierInvoiceRequestDto request) {
        SupplierInvoiceDto invoice = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/supplier-invoices/" + invoice.id()))
            .body(invoice);
    }

    @PostMapping("/{id}/post")
    public SupplierInvoiceDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    public SupplierInvoiceDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
