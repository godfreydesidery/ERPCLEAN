package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.Branch;

/** A branch the current user may switch into, for the app-shell branch picker. */
public record AccessibleBranchDto(
    Long id,
    String code,
    String name,
    String type
) {
    public static AccessibleBranchDto from(Branch branch) {
        return new AccessibleBranchDto(
            branch.getId(),
            branch.getCode(),
            branch.getName(),
            branch.getType().name()
        );
    }
}
