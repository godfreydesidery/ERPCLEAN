package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Creates an employee. Two paths:
 * <ul>
 *   <li><b>Pick existing party</b>: supply {@code partyId} only; {@code party}
 *       is ignored.</li>
 *   <li><b>Create new party</b>: leave {@code partyId} null and supply
 *       {@code party}. The backend allocates the party code from the
 *       {@code EMP} sequence; clients cannot override it.</li>
 * </ul>
 */
public record CreateEmployeeRequestDto(
    Long partyId,
    @Valid PartyDetailsDto party,
    @NotBlank @Size(max = 40) String employeeCode,
    Long appUserId,
    @Size(max = 120) String jobTitle,
    @NotNull Long branchId,
    LocalDate hireDate,
    LocalDate terminationDate
) {}
