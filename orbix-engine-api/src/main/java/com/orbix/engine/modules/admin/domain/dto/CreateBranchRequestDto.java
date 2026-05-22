package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating a branch within the caller's company. */
public record CreateBranchRequestDto(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 32) String type,
    @Size(max = 4000) String physicalAddress,
    @Size(max = 40) String phone,
    @NotBlank @Size(max = 64) String timeZone
) {}
