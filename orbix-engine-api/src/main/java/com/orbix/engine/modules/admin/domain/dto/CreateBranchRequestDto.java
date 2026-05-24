package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.enums.BranchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for creating a branch within the caller's company. */
public record CreateBranchRequestDto(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 120) String name,
    @NotNull BranchType type,
    @Size(max = 4000) String physicalAddress,
    @Size(max = 40) String phone,
    @NotBlank @Size(max = 64) String timeZone
) {}
