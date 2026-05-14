package com.orbix.engine.modules.catalog.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating an item group. {@code parentId} null = a root group. */
public record CreateItemGroupRequestDto(
    Long parentId,
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 120) String name
) {}
