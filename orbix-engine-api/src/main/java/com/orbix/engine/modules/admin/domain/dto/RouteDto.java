package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.Route;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;

/** Route as returned by the admin route endpoints. */
public record RouteDto(
    Long id,
    Long companyId,
    String code,
    String name,
    String description,
    AdminStatus status
) {
    public static RouteDto from(Route route) {
        return new RouteDto(
            route.getId(),
            route.getCompanyId(),
            route.getCode(),
            route.getName(),
            route.getDescription(),
            route.getStatus()
        );
    }
}
