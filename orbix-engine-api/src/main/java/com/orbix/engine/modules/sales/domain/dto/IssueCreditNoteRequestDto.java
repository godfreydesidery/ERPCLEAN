package com.orbix.engine.modules.sales.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueCreditNoteRequestDto(
    @NotBlank String number,
    String notes
) {}
