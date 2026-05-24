package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason captured when a branch is activated or deactivated (audit trail). */
public record BranchStatusChangeRequestDto(
    @NotBlank @Size(max = 200) String reason
) {}
