package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Creates an employee. If a party in the company already carries the supplied
 * {@code party.tin()}, that party is reused (shared-party rule) and {@code code}
 * is ignored.
 */
public record CreateEmployeeRequestDto(
    @NotBlank @Size(max = 40) String code,
    @Valid @NotNull PartyDetailsDto party,
    @NotBlank @Size(max = 40) String employeeCode,
    Long appUserId,
    @Size(max = 120) String jobTitle,
    @NotNull Long branchId,
    LocalDate hireDate,
    LocalDate terminationDate
) {}
