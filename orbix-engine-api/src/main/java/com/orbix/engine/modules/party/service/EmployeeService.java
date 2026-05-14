package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateEmployeeRequestDto;

import java.util.List;

/** Employee-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface EmployeeService {

    List<EmployeeResponseDto> listEmployees();

    EmployeeResponseDto getEmployee(Long partyId);

    EmployeeResponseDto createEmployee(CreateEmployeeRequestDto request);

    EmployeeResponseDto updateEmployee(Long partyId, UpdateEmployeeRequestDto request);

    void deactivateEmployee(Long partyId);
}
