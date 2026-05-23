package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.springframework.data.domain.Pageable;

/** Supplier-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface SupplierService {

    PageDto<SupplierResponseDto> listSuppliers(String q, PartyStatus status, Pageable pageable);

    SupplierResponseDto getSupplierByPartyUid(String partyUid);

    SupplierResponseDto createSupplier(CreateSupplierRequestDto request);

    SupplierResponseDto updateSupplierByPartyUid(String partyUid, UpdateSupplierRequestDto request);

    void deactivateSupplierByPartyUid(String partyUid);

    /** Reactivates the underlying party (affects every role on it). */
    void activateSupplierByPartyUid(String partyUid);
}
