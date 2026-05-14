package com.orbix.engine.modules.day.domain.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Opens a branch business day for the given date. */
public record OpenDayRequestDto(
    @NotNull LocalDate businessDate
) {}
