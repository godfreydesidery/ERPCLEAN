package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.CompanyDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateCompanyRequestDto;
import com.orbix.engine.modules.admin.service.CompanyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The caller's company profile (US-COMP-002). Singular resource — it always
 * refers to the company in the request context. Gated by {@code ADMIN.MANAGE_SETTINGS}.
 */
@RestController
@RequestMapping("/api/v1/company")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_SETTINGS')")
public class CompanyController {

    private final CompanyService service;

    @GetMapping
    public CompanyDto getCurrent() {
        return service.getCurrent();
    }

    @PatchMapping
    public CompanyDto update(@Valid @RequestBody UpdateCompanyRequestDto request) {
        return service.updateCurrent(request);
    }
}
