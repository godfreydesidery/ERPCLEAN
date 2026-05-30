package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.enums.BranchType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for editing a branch. The branch code is immutable. */
public record UpdateBranchRequestDto(
    @NotBlank @Size(max = 120) String name,
    @NotNull BranchType type,
    @Size(max = 4000) String physicalAddress,
    @Size(max = 40) String phone,
    @NotBlank @Size(max = 64) String timeZone,
    /** When {@code true}, makes this branch the default for its company,
     *  clearing the flag on any other branch. {@code null} / {@code false}
     *  leaves the current default unchanged. */
    Boolean isDefault
) {}
