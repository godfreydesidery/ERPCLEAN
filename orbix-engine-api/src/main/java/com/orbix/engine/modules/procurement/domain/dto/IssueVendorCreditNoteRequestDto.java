package com.orbix.engine.modules.procurement.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record IssueVendorCreditNoteRequestDto(
    @NotBlank String number,
    String notes
) {}
