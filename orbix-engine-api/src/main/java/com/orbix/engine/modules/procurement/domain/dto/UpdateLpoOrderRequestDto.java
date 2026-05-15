package com.orbix.engine.modules.procurement.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Edits an LPO while it is in DRAFT — replaces the header + all lines. */
public record UpdateLpoOrderRequestDto(
    @NotNull Long supplierId,
    @NotNull LocalDate orderDate,
    LocalDate expectedDeliveryDate,
    @NotBlank String currencyCode,
    String notes,
    @NotEmpty @Valid List<CreateLpoOrderRequestDto.Line> lines
) {}
