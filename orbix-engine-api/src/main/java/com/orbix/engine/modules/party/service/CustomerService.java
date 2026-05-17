package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;

import java.util.List;

/** Customer-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface CustomerService {

    List<CustomerResponseDto> listCustomers();

    CustomerResponseDto getCustomer(Long partyId);

    CustomerResponseDto createCustomer(CreateCustomerRequestDto request);

    CustomerResponseDto updateCustomer(Long partyId, UpdateCustomerRequestDto request);

    /** Deactivates the underlying party (affects every role on it). */
    void deactivateCustomer(Long partyId);

    /** Reactivates the underlying party (affects every role on it). */
    void activateCustomer(Long partyId);

    /** Provisions the synthetic per-branch walk-in customer; idempotent-friendly caller. */
    void createWalkInCustomer(Long branchId);
}
