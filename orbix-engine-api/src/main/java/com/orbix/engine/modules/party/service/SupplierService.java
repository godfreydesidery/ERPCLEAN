package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSupplierRequestDto;

import java.util.List;

/** Supplier-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface SupplierService {

    List<SupplierResponseDto> listSuppliers();

    SupplierResponseDto getSupplier(Long partyId);

    SupplierResponseDto createSupplier(CreateSupplierRequestDto request);

    SupplierResponseDto updateSupplier(Long partyId, UpdateSupplierRequestDto request);

    void deactivateSupplier(Long partyId);

    /** Reactivates the underlying party (affects every role on it). */
    void activateSupplier(Long partyId);
}
