package com.orbix.engine.modules.common.domain.dto;

import com.orbix.engine.modules.common.domain.enums.SettingType;

/**
 * A configurable setting as shown on the settings screen: its effective value
 * plus enough metadata for the UI to render and validate it, and whether the
 * effective value comes from a stored override or the compiled-in default.
 */
public record SettingDto(
    String code,
    String group,
    String label,
    String description,
    SettingType type,
    String value,
    String defaultValue,
    boolean overridden
) {}
