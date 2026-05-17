package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.VatGroupDto;

import java.util.List;

/**
 * VAT-group registry (F1.4), company-scoped. At most one group per company may
 * be the default; setting a new default clears the previous one.
 */
public interface VatGroupService {

    List<VatGroupDto> listVatGroups();

    VatGroupDto getVatGroupByUid(String uid);

    VatGroupDto createVatGroup(CreateVatGroupRequestDto request);

    VatGroupDto updateVatGroupByUid(String uid, UpdateVatGroupRequestDto request);

    void archiveVatGroupByUid(String uid);
}
