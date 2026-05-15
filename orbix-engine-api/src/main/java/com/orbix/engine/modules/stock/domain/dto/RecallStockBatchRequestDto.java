package com.orbix.engine.modules.stock.domain.dto;

import jakarta.validation.constraints.NotBlank;

/** Recall the remaining on-hand qty of an ACTIVE batch and write it off. */
public record RecallStockBatchRequestDto(
    @NotBlank String reason
) {}
