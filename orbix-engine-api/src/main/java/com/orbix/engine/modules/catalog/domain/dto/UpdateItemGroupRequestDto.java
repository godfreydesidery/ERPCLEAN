package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for renaming an item group. Code and parent are changed via other endpoints. */
public record UpdateItemGroupRequestDto(
    @NotBlank @Size(max = 120) String name
) {}
