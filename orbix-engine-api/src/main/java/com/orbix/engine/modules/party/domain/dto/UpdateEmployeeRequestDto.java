package com.orbix.engine.modules.party.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Edits an employee and its underlying party. Party code + employee code are immutable. */
public record UpdateEmployeeRequestDto(
    @Valid @NotNull PartyDetailsDto party,
    Long appUserId,
    @Size(max = 120) String jobTitle,
    @NotNull Long branchId,
    LocalDate hireDate,
    LocalDate terminationDate
) {}
