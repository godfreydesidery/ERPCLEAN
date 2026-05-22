package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for editing a unit of measure. The code is immutable. */
public record UpdateUomRequestDto(
    @NotBlank @Size(max = 80) String name,
    @NotNull UomDimension dimension,
    boolean base
) {}
