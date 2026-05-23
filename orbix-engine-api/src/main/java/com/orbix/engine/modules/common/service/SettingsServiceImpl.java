package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.dto.SettingDto;
import com.orbix.engine.modules.common.domain.dto.UpdateSettingsRequestDto;
import com.orbix.engine.modules.common.domain.entity.AppSetting;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.domain.enums.SettingType;
import com.orbix.engine.modules.common.repository.AppSettingRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final AppSettingRepository repo;

    /** code -> stored override value. Settings change rarely; this avoids a DB hit per transaction. */
    private final ConcurrentMap<String, String> overrides = new ConcurrentHashMap<>();

    @PostConstruct
    void load() {
        repo.findAll().forEach(s -> overrides.put(s.getSettingKey(), s.getValue()));
    }

    private String raw(SettingKey key) {
        return overrides.getOrDefault(key.code(), key.defaultValue());
    }

    @Override
    public String getString(SettingKey key) {
        return raw(key);
    }

    @Override
    public BigDecimal getDecimal(SettingKey key) {
        return new BigDecimal(raw(key).trim());
    }

    @Override
    public int getInt(SettingKey key) {
        return Integer.parseInt(raw(key).trim());
    }

    @Override
    public boolean getBoolean(SettingKey key) {
        return Boolean.parseBoolean(raw(key).trim());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SettingDto> listAll() {
        List<SettingDto> out = new ArrayList<>();
        for (SettingKey k : SettingKey.values()) {
            out.add(new SettingDto(
                k.code(), k.group(), k.label(), k.description(), k.type(),
                raw(k), k.defaultValue(), overrides.containsKey(k.code())));
        }
        return out;
    }

    @Override
    @Transactional
    public List<SettingDto> update(UpdateSettingsRequestDto request, Long actorId) {
        for (UpdateSettingsRequestDto.Item item : request.items()) {
            SettingKey key = SettingKey.byCode(item.code())
                .orElseThrow(() -> new IllegalArgumentException("Unknown setting: " + item.code()));
            String value = item.value() == null ? null : item.value().trim();

            if (value == null || value.isEmpty()) {
                // Reset to compiled-in default.
                repo.deleteById(key.code());
                overrides.remove(key.code());
                continue;
            }
            validate(key, value);
            repo.save(new AppSetting(key.code(), value, actorId));
            overrides.put(key.code(), value);
        }
        return listAll();
    }

    private static void validate(SettingKey key, String value) {
        SettingType type = key.type();
        try {
            switch (type) {
                case PERCENT, MONEY -> {
                    BigDecimal n = new BigDecimal(value);
                    if (n.signum() < 0) {
                        throw new IllegalArgumentException(key.label() + " must not be negative");
                    }
                }
                case DECIMAL -> new BigDecimal(value);
                case INTEGER, DAYS -> Integer.parseInt(value);
                case BOOLEAN -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                        throw new IllegalArgumentException(key.label() + " must be true or false");
                    }
                }
                case STRING -> { /* any */ }
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key.label() + ": '" + value + "' is not a valid " + type);
        }
    }
}
