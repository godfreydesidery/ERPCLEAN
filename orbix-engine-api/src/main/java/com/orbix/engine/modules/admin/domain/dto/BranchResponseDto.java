package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;

/** Branch as returned by the admin branch-management endpoints. */
public record BranchResponseDto(
    Long id,
    Long companyId,
    String code,
    String name,
    String type,
    String physicalAddress,
    String phone,
    String timeZone,
    boolean isDefault,
    AdminStatus status
) {
    public static BranchResponseDto from(Branch branch) {
        return new BranchResponseDto(
            branch.getId(),
            branch.getCompanyId(),
            branch.getCode(),
            branch.getName(),
            branch.getType(),
            branch.getPhysicalAddress(),
            branch.getPhone(),
            branch.getTimeZone(),
            branch.isDefault(),
            branch.getStatus()
        );
    }
}
