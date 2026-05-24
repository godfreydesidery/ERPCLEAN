package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.orders.domain.dto.CancelCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CreateCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.CustomerOrderDto;
import com.orbix.engine.modules.orders.domain.dto.PatchCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.dto.PayCustomerOrderRequestDto;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import com.orbix.engine.modules.orders.service.CustomerOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Layby + pre-order endpoints (F7.2 / US-ORD-001..009). Two permission gates —
 * {@code ORDER.MANAGE} for back-office / cashier flows (create, edit, pay,
 * cancel) and {@code ORDER.COLLECT} for the final at-till collection so a POS
 * device can collect without the broader MANAGE grant.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
public class CustomerOrderController {

    private final CustomerOrderService service;

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public ResponseEntity<CustomerOrderDto> create(
            @Valid @RequestBody CreateCustomerOrderRequestDto request) {
        CustomerOrderDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/uid/" + created.uid())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER.READ') or hasAuthority('ORDER.MANAGE')")
    public List<CustomerOrderDto> list(@RequestParam(required = false) Long branchId,
                                       @RequestParam(required = false) Long customerId,
                                       @RequestParam(required = false) CustomerOrderStatus status,
                                       @RequestParam(required = false) CustomerOrderType type) {
        return service.list(branchId, customerId, status, type);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('ORDER.READ') or hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto patch(@PathVariable @ValidUlid String uid,
                                  @Valid @RequestBody PatchCustomerOrderRequestDto request) {
        return service.patch(uid, request);
    }

    @PostMapping("/uid/{uid}/reserve")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto reserve(@PathVariable @ValidUlid String uid) {
        return service.reserve(uid);
    }

    @PostMapping("/uid/{uid}/payments")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto pay(@PathVariable @ValidUlid String uid,
                                @Valid @RequestBody PayCustomerOrderRequestDto request) {
        return service.pay(uid, request);
    }

    @PostMapping("/uid/{uid}/cancel")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto cancel(@PathVariable @ValidUlid String uid,
                                   @Valid @RequestBody CancelCustomerOrderRequestDto request) {
        return service.cancel(uid, request);
    }

    /**
     * Manual READY transition for PRE_ORDER — replaces the (deferred)
     * {@code ProductionOutputPosted.v1} subscriber until F7.3 lands.
     */
    @PostMapping("/uid/{uid}/ready")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto markReady(@PathVariable @ValidUlid String uid) {
        return service.markReady(uid);
    }

    @PostMapping("/uid/{uid}/collect")
    @PreAuthorize("hasAuthority('ORDER.COLLECT') or hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto collect(@PathVariable @ValidUlid String uid) {
        return service.collect(uid);
    }
}
