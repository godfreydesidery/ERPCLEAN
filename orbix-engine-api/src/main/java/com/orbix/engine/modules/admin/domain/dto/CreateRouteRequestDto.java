package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating a delivery route within the caller's company. */
public record CreateRouteRequestDto(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 4000) String description
) {}
