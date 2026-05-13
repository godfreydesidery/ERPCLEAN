package com.orbix.engine.catalog.api.dto;

import com.orbix.engine.catalog.domain.Item;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateItemRequest(
    @NotBlank @Size(max = 40) String code,
    @NotBlank @Size(max = 200) String name,
    @NotNull Item.Type type,
    @NotNull Long itemGroupId,
    @NotNull Long uomId,
    @NotNull Long vatGroupId
) {}
