package com.orbix.engine.modules.iam.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating a new (non-system) role. */
public record CreateRoleRequestDto(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 2000) String description
) {}
