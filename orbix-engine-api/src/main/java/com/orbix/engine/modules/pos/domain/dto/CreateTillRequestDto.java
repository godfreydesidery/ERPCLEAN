package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTillRequestDto(
    @NotNull Long branchId,
    @NotBlank String code,
    @NotBlank String name,
    @NotNull Long defaultPriceListId,
    String installId
) {}
