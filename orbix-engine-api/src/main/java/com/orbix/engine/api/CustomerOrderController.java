package com.orbix.engine.api;

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
public class CustomerOrderController {

    private final CustomerOrderService service;

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public ResponseEntity<CustomerOrderDto> create(
            @Valid @RequestBody CreateCustomerOrderRequestDto request) {
        CustomerOrderDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ORDER.READ') or hasAuthority('ORDER.MANAGE')")
    public List<CustomerOrderDto> list(@RequestParam(required = false) Long branchId,
                                       @RequestParam(required = false) Long customerId,
                                       @RequestParam(required = false) CustomerOrderStatus status,
                                       @RequestParam(required = false) CustomerOrderType type) {
        return service.list(branchId, customerId, status, type);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER.READ') or hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto patch(@PathVariable Long id,
                                  @Valid @RequestBody PatchCustomerOrderRequestDto request) {
        return service.patch(id, request);
    }

    @PostMapping("/{id}/reserve")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto reserve(@PathVariable Long id) {
        return service.reserve(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto pay(@PathVariable Long id,
                                @Valid @RequestBody PayCustomerOrderRequestDto request) {
        return service.pay(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto cancel(@PathVariable Long id,
                                   @Valid @RequestBody CancelCustomerOrderRequestDto request) {
        return service.cancel(id, request);
    }

    /**
     * Manual READY transition for PRE_ORDER — replaces the (deferred)
     * {@code ProductionOutputPosted.v1} subscriber until F7.3 lands.
     */
    @PostMapping("/{id}/ready")
    @PreAuthorize("hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto markReady(@PathVariable Long id) {
        return service.markReady(id);
    }

    @PostMapping("/{id}/collect")
    @PreAuthorize("hasAuthority('ORDER.COLLECT') or hasAuthority('ORDER.MANAGE')")
    public CustomerOrderDto collect(@PathVariable Long id) {
        return service.collect(id);
    }
}
