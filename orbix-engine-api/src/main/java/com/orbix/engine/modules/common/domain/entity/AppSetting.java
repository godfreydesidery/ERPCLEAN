package com.orbix.engine.modules.common.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single global configuration override. The presence of a row means the
 * deployment has overridden the compiled-in default for {@code settingKey};
 * absence means "use the default" (see {@code SettingsService} / {@code SettingKey}).
 * The value is stored as text and parsed per the key's declared type.
 */
@Entity
@Table(name = "app_setting")
@Getter
@Setter
@NoArgsConstructor
public class AppSetting {

    @Id
    @Column(name = "setting_key", length = 120)
    private String settingKey;

    @Column(name = "value", nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    public AppSetting(String settingKey, String value, Long updatedBy) {
        this.settingKey = settingKey;
        this.value = value;
        this.updatedAt = Instant.now();
        this.updatedBy = updatedBy;
    }
}
