package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.SettingDto;
import com.orbix.engine.modules.common.domain.dto.UpdateSettingsRequestDto;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Global configuration defaults, editable from the UI. Gated by
 * {@code ADMIN.MANAGE_SETTINGS}. Values resolve as override-then-default; a
 * blank value on update resets that key to its compiled-in default.
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_SETTINGS')")
public class SettingsController {

    private final SettingsService service;
    private final RequestContext context;

    @GetMapping
    public List<SettingDto> list() {
        return service.listAll();
    }

    @PutMapping
    public List<SettingDto> update(@Valid @RequestBody UpdateSettingsRequestDto request) {
        return service.update(request, context.userId());
    }
}
