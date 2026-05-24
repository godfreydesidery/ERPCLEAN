package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.CreateTillRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillDto;
import com.orbix.engine.modules.pos.domain.dto.UpdateTillRequestDto;

import java.util.List;

/** Till master data (F5.1). Gated by {@code POS.MANAGE_TILL} at the controller. */
public interface TillService {

    TillDto create(CreateTillRequestDto request);

    TillDto update(String uid, UpdateTillRequestDto request);

    TillDto deactivate(String uid);

    TillDto activate(String uid);

    List<TillDto> list(Long branchId);

    TillDto get(String uid);
}
