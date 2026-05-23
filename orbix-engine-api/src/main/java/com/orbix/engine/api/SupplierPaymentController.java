package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.CreateSupplierPaymentRequestDto;
import com.orbix.engine.modules.cash.domain.dto.SupplierPaymentDto;
import com.orbix.engine.modules.cash.service.SupplierPaymentService;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Supplier payments (F3.4). Gated by {@code CASH.MANAGE_SUPPLIER_PAYMENT}. */
@RestController
@RequestMapping("/api/v1/supplier-payments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CASH.MANAGE_SUPPLIER_PAYMENT')")
public class SupplierPaymentController {

    private final SupplierPaymentService service;

    @GetMapping
    public PageDto<SupplierPaymentDto> list(@RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public SupplierPaymentDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<SupplierPaymentDto> create(
            @Valid @RequestBody CreateSupplierPaymentRequestDto request) {
        SupplierPaymentDto payment = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/supplier-payments/" + payment.id()))
            .body(payment);
    }

    @PostMapping("/{id}/post")
    public SupplierPaymentDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    public SupplierPaymentDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
