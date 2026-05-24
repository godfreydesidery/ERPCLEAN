package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Reason captured when a route is activated or deactivated (audit trail). */
public record RouteStatusChangeRequestDto(
    @NotBlank @Size(max = 200) String reason
) {}
