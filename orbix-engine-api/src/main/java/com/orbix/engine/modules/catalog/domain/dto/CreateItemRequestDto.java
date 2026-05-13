package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateItemRequestDto(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 200) String name,
    @NotNull ItemType type,
    @NotNull Long itemGroupId,
    @NotNull Long uomId,
    @NotNull Long vatGroupId
) {}
