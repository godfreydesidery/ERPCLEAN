package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.SettingDto;
import com.orbix.engine.modules.common.domain.dto.UpdateSettingsRequestDto;
import com.orbix.engine.modules.common.domain.enums.SettingKey;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resolves globally-configurable defaults: a stored {@code app_setting} override
 * wins, otherwise the compiled-in {@link SettingKey#defaultValue()}. Business
 * code reads through the typed getters so changes made in the UI take effect on
 * the next call without a restart.
 */
public interface SettingsService {

    String getString(SettingKey key);

    BigDecimal getDecimal(SettingKey key);

    int getInt(SettingKey key);

    boolean getBoolean(SettingKey key);

    /** Every setting with its effective value + metadata, for the settings screen. */
    List<SettingDto> listAll();

    /** Apply a batch of overrides (blank value = reset to default); returns the refreshed list. */
    List<SettingDto> update(UpdateSettingsRequestDto request, Long actorId);
}
