package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin payload to patch a user's profile. Username is never editable. */
public record UpdateUserRequestDto(
    @NotBlank @Size(min = 2, max = 120) String displayName,
    @Size(max = 120) String email,
    @Size(max = 40) String phone,
    Long defaultBranchId
) {}
