package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for editing a delivery route. The route code is immutable. */
public record UpdateRouteRequestDto(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 4000) String description
) {}
