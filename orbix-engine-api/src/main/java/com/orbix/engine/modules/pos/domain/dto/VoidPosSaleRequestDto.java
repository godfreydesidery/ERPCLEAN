package com.orbix.engine.modules.pos.domain.dto;

import jakarta.validation.constraints.NotBlank;

/** Void a POSTED POS sale on the same business day (F5.3). */
public record VoidPosSaleRequestDto(
    @NotBlank String reason
) {}
