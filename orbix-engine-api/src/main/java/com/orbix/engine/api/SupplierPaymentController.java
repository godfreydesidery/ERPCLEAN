package com.orbix.engine.api;

import com.orbix.engine.modules.cash.domain.dto.CreateSupplierPaymentRequestDto;
import com.orbix.engine.modules.cash.domain.dto.SupplierPaymentDto;
import com.orbix.engine.modules.cash.service.SupplierPaymentService;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** Supplier payments (F3.4). Gated by {@code CASH.MANAGE_SUPPLIER_PAYMENT}. */
@RestController
@RequestMapping("/api/v1/supplier-payments")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('CASH.MANAGE_SUPPLIER_PAYMENT')")
public class SupplierPaymentController {

    private final SupplierPaymentService service;

    @GetMapping
    public PageDto<SupplierPaymentDto> list(@RequestParam(required = false) Long branchId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    public SupplierPaymentDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<SupplierPaymentDto> create(
            @Valid @RequestBody CreateSupplierPaymentRequestDto request) {
        SupplierPaymentDto payment = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/supplier-payments/uid/" + payment.uid()))
            .body(payment);
    }

    @PostMapping("/uid/{uid}/post")
    public SupplierPaymentDto post(@PathVariable @ValidUlid String uid) {
        return service.post(uid);
    }

    @PostMapping("/uid/{uid}/cancel")
    public SupplierPaymentDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }
}
