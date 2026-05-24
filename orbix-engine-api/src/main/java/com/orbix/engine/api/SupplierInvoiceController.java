package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.procurement.domain.dto.CreateSupplierInvoiceRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import com.orbix.engine.modules.procurement.service.SupplierInvoiceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Supplier invoice + 3-way match (F3.3). Gated by {@code PROCUREMENT.MANAGE_INVOICE}. */
@RestController
@RequestMapping("/api/v1/supplier-invoices")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_INVOICE')")
public class SupplierInvoiceController {

    private final SupplierInvoiceService service;

    @GetMapping
    public PageDto<SupplierInvoiceDto> list(@RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    public SupplierInvoiceDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<SupplierInvoiceDto> create(
            @Valid @RequestBody CreateSupplierInvoiceRequestDto request) {
        SupplierInvoiceDto invoice = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/supplier-invoices/uid/" + invoice.uid()))
            .body(invoice);
    }

    @PostMapping("/uid/{uid}/post")
    public SupplierInvoiceDto post(@PathVariable @ValidUlid String uid) {
        return service.post(uid);
    }

    @PostMapping("/uid/{uid}/cancel")
    public SupplierInvoiceDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }
}
