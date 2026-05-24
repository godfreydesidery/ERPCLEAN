package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;

import java.util.List;

/** Unit-of-measure registry (F1.4). UoM is the one global, non-company-scoped catalog table. */
public interface UomService {

    List<UomDto> listUoms();

    UomDto getUomByUid(String uid);

    UomDto createUom(CreateUomRequestDto request);

    UomDto updateUomByUid(String uid, UpdateUomRequestDto request);

    void archiveUomByUid(String uid);

    void activateUomByUid(String uid);
}
