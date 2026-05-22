package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for editing a branch. The branch code is immutable. */
public record UpdateBranchRequestDto(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 32) String type,
    @Size(max = 4000) String physicalAddress,
    @Size(max = 40) String phone,
    @NotBlank @Size(max = 64) String timeZone
) {}
