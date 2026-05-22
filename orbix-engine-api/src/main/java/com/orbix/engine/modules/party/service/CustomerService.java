package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateCustomerRequestDto;

import java.util.List;

/** Customer-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface CustomerService {

    List<CustomerResponseDto> listCustomers();

    CustomerResponseDto getCustomerByPartyUid(String partyUid);

    CustomerResponseDto createCustomer(CreateCustomerRequestDto request);

    CustomerResponseDto updateCustomerByPartyUid(String partyUid, UpdateCustomerRequestDto request);

    /** Deactivates the underlying party (affects every role on it). */
    void deactivateCustomerByPartyUid(String partyUid);

    /** Reactivates the underlying party (affects every role on it). */
    void activateCustomerByPartyUid(String partyUid);

    /** Provisions the synthetic per-branch walk-in customer; idempotent-friendly caller. */
    void createWalkInCustomer(Long branchId);
}
