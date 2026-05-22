package com.orbix.engine.modules.sales.domain.dto;

import jakarta.validation.constraints.NotBlank;

/** Void a POSTED sales invoice — only allowed on the same business day. */
public record VoidSalesInvoiceRequestDto(
    @NotBlank String reason
) {}
