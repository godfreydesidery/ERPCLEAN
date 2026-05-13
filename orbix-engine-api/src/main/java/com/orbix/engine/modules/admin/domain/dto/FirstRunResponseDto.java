package com.orbix.engine.modules.admin.domain.dto;

public record FirstRunResponseDto(
    Long organisationId,
    Long companyId,
    String companyCode,
    Long branchId,
    String branchCode,
    Long defaultSectionId,
    Long adminUserId,
    String adminUsername
) {}
