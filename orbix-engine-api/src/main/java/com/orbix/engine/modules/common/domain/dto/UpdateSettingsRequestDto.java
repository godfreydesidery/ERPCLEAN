package com.orbix.engine.modules.common.domain.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Batch update of settings. A null/blank {@code value} resets that key to its
 * compiled-in default (removes the override).
 */
public record UpdateSettingsRequestDto(
    @NotEmpty List<Item> items
) {
    public record Item(String code, String value) {}
}
