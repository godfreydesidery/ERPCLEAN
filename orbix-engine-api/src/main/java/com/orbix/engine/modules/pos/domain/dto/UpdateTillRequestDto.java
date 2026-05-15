package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateTillRequestDto(
    @NotBlank String name,
    @NotNull Long defaultPriceListId,
    String installId
) {}
