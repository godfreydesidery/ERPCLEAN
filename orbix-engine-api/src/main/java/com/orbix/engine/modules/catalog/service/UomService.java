package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;

import java.util.List;

/** Unit-of-measure registry (F1.4). UoM is the one global, non-company-scoped catalog table. */
public interface UomService {

    List<UomDto> listUoms();

    UomDto getUom(Long uomId);

    UomDto createUom(CreateUomRequestDto request);

    UomDto updateUom(Long uomId, UpdateUomRequestDto request);
}
