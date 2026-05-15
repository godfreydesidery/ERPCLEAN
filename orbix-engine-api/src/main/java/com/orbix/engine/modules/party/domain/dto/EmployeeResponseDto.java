package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.entity.Employee;
import com.orbix.engine.modules.party.domain.entity.Party;

import java.time.LocalDate;

public record EmployeeResponseDto(
    Long partyId,
    PartyResponseDto party,
    Long appUserId,
    String employeeCode,
    String jobTitle,
    Long branchId,
    LocalDate hireDate,
    LocalDate terminationDate
) {
    public static EmployeeResponseDto from(Employee employee, Party party) {
        return new EmployeeResponseDto(
            employee.getPartyId(),
            PartyResponseDto.from(party),
            employee.getAppUserId(),
            employee.getEmployeeCode(),
            employee.getJobTitle(),
            employee.getBranchId(),
            employee.getHireDate(),
            employee.getTerminationDate()
        );
    }
}
