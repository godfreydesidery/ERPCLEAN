package com.orbix.engine.api;

import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;
import com.orbix.engine.modules.party.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Customer management (F1.7). Gated by {@code PARTY.MANAGE_CUSTOMERS}. */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_CUSTOMERS')")
public class CustomerController {

    private final CustomerService service;

    @GetMapping
    public List<CustomerResponseDto> listCustomers() {
        return service.listCustomers();
    }

    @GetMapping("/{partyId}")
    public CustomerResponseDto getCustomer(@PathVariable Long partyId) {
        return service.getCustomer(partyId);
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDto> createCustomer(
            @Valid @RequestBody CreateCustomerRequestDto request) {
        CustomerResponseDto customer = service.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + customer.partyId()))
            .body(customer);
    }

    @PatchMapping("/{partyId}")
    public CustomerResponseDto updateCustomer(@PathVariable Long partyId,
                                              @Valid @RequestBody UpdateCustomerRequestDto request) {
        return service.updateCustomer(partyId, request);
    }

    @PostMapping("/{partyId}/deactivate")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable Long partyId) {
        service.deactivateCustomer(partyId);
        return ResponseEntity.noContent().build();
    }
}
