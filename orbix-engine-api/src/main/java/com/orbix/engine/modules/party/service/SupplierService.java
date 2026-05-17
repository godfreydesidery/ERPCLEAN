package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSupplierRequestDto;

import java.util.List;

/** Supplier-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface SupplierService {

    List<SupplierResponseDto> listSuppliers();

    SupplierResponseDto getSupplierByPartyUid(String partyUid);

    SupplierResponseDto createSupplier(CreateSupplierRequestDto request);

    SupplierResponseDto updateSupplierByPartyUid(String partyUid, UpdateSupplierRequestDto request);

    void deactivateSupplierByPartyUid(String partyUid);

    /** Reactivates the underlying party (affects every role on it). */
    void activateSupplierByPartyUid(String partyUid);
}
