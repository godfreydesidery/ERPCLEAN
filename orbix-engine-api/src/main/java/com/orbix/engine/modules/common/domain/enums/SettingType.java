package com.orbix.engine.modules.common.domain.enums;

/**
 * Wire/UI type of a configurable setting. Drives input rendering on the web
 * settings screen and value validation in {@code SettingsService}.
 */
public enum SettingType {
    /** A percentage, e.g. 10 means 10%. */
    PERCENT,
    /** A monetary amount (non-negative decimal). */
    MONEY,
    /** A general decimal / fraction, e.g. 0.005. */
    DECIMAL,
    /** A whole number. */
    INTEGER,
    /** A whole number of days. */
    DAYS,
    /** true / false. */
    BOOLEAN,
    /** Free text. */
    STRING
}
