package com.orbix.engine.modules.orders.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.Instant;
import java.util.List;

/**
 * Edit-while-DRAFT payload. Lines fully replace the existing line set.
 * {@code reservedUntil} and {@code notes} are also patchable while DRAFT.
 * Anything else (customer / branch / type) requires a re-create.
 */
public record PatchCustomerOrderRequestDto(
    @NotEmpty @Valid List<CreateCustomerOrderRequestDto.Line> lines,
    Instant reservedUntil,
    String notes
) {}
