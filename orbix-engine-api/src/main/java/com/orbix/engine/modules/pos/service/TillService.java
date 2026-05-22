package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.CreateTillRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillDto;
import com.orbix.engine.modules.pos.domain.dto.UpdateTillRequestDto;

import java.util.List;

/** Till master data (F5.1). Gated by {@code POS.MANAGE_TILL} at the controller. */
public interface TillService {

    TillDto create(CreateTillRequestDto request);

    TillDto update(Long tillId, UpdateTillRequestDto request);

    TillDto deactivate(Long tillId);

    TillDto activate(Long tillId);

    List<TillDto> list(Long branchId);

    TillDto get(Long tillId);
}
