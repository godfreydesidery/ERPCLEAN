package com.orbix.engine.modules.catalog.domain.dto;

import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload for editing an item. The item code is immutable. */
public record UpdateItemRequestDto(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 80) String shortName,
    @NotNull ItemType type,
    @NotNull Long itemGroupId,
    @NotNull Long uomId,
    @NotNull Long vatGroupId,
    boolean tracked,
    @PositiveOrZero BigDecimal minSellPrice
) {}
