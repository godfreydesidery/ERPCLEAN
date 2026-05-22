package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateEmployeeRequestDto;

import java.util.List;

/** Employee-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface EmployeeService {

    List<EmployeeResponseDto> listEmployees();

    EmployeeResponseDto getEmployeeByPartyUid(String partyUid);

    EmployeeResponseDto createEmployee(CreateEmployeeRequestDto request);

    EmployeeResponseDto updateEmployeeByPartyUid(String partyUid, UpdateEmployeeRequestDto request);

    void deactivateEmployeeByPartyUid(String partyUid);

    /** Reactivates the underlying party (affects every role on it). */
    void activateEmployeeByPartyUid(String partyUid);
}
