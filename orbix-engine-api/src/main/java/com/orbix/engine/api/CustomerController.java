package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Customer management (F1.7). Per-action permissions:
 * {@code CUSTOMER.CREATE}, {@code CUSTOMER.UPDATE}, {@code CUSTOMER.ARCHIVE}
 * (archive perm also gates re-activation, the inverse of archive).
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Validated
public class CustomerController {

    private final CustomerService service;

    @GetMapping
    public PageDto<CustomerResponseDto> listCustomers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PartyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listCustomers(q, status, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{partyUid}")
    public CustomerResponseDto getCustomer(@PathVariable @ValidUlid String partyUid) {
        return service.getCustomerByPartyUid(partyUid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER.CREATE')")
    public ResponseEntity<CustomerResponseDto> createCustomer(
            @Valid @RequestBody CreateCustomerRequestDto request) {
        CustomerResponseDto customer = service.createCustomer(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/uid/" + customer.party().uid()))
            .body(customer);
    }

    @PatchMapping("/uid/{partyUid}")
    @PreAuthorize("hasAuthority('CUSTOMER.UPDATE')")
    public CustomerResponseDto updateCustomer(@PathVariable @ValidUlid String partyUid,
                                              @Valid @RequestBody UpdateCustomerRequestDto request) {
        return service.updateCustomerByPartyUid(partyUid, request);
    }

    @PostMapping("/uid/{partyUid}/archive")
    @PreAuthorize("hasAuthority('CUSTOMER.ARCHIVE')")
    public ResponseEntity<Void> archiveCustomer(@PathVariable @ValidUlid String partyUid) {
        service.archiveCustomerByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{partyUid}/activate")
    @PreAuthorize("hasAuthority('CUSTOMER.ARCHIVE')")
    public ResponseEntity<Void> activateCustomer(@PathVariable @ValidUlid String partyUid) {
        service.activateCustomerByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }
}
