package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.party.domain.dto.CreateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.dto.EmployeeResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateEmployeeRequestDto;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import org.springframework.data.domain.Pageable;

/** Employee-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface EmployeeService {

    PageDto<EmployeeResponseDto> listEmployees(String q, PartyStatus status, Pageable pageable);

    EmployeeResponseDto getEmployeeByPartyUid(String partyUid);

    EmployeeResponseDto createEmployee(CreateEmployeeRequestDto request);

    EmployeeResponseDto updateEmployeeByPartyUid(String partyUid, UpdateEmployeeRequestDto request);

    /** Archives the underlying party (affects every role on it). */
    void archiveEmployeeByPartyUid(String partyUid);

    /** Restores the underlying party to ACTIVE (affects every role on it). */
    void activateEmployeeByPartyUid(String partyUid);
}
