package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;
import com.orbix.engine.modules.party.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Customer management (F1.7). Gated by {@code PARTY.MANAGE_CUSTOMERS}. */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_CUSTOMERS')")
@Validated
public class CustomerController {

    private final CustomerService service;

    @GetMapping
    public List<CustomerResponseDto> listCustomers() {
        return service.listCustomers();
    }

    @GetMapping("/uid/{partyUid}")
    public CustomerResponseDto getCustomer(@PathVariable @ValidUlid String partyUid) {
        return service.getCustomerByPartyUid(partyUid);
    }

    @PostMapping
    public ResponseEntity<CustomerResponseDto> createCustomer(
            @Valid @RequestBody CreateCustomerRequestDto request) {
        CustomerResponseDto customer = service.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/uid/" + customer.party().uid()))
            .body(customer);
    }

    @PatchMapping("/uid/{partyUid}")
    public CustomerResponseDto updateCustomer(@PathVariable @ValidUlid String partyUid,
                                              @Valid @RequestBody UpdateCustomerRequestDto request) {
        return service.updateCustomerByPartyUid(partyUid, request);
    }

    @PostMapping("/uid/{partyUid}/deactivate")
    public ResponseEntity<Void> deactivateCustomer(@PathVariable @ValidUlid String partyUid) {
        service.deactivateCustomerByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{partyUid}/activate")
    public ResponseEntity<Void> activateCustomer(@PathVariable @ValidUlid String partyUid) {
        service.activateCustomerByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }
}
