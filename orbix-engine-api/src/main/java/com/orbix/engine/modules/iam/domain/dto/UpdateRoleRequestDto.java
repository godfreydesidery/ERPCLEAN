package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for editing a role's display name / description. The code is immutable. */
public record UpdateRoleRequestDto(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String description
) {}
