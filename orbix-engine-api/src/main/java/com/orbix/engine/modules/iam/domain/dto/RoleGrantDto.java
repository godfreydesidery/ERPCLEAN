package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.UserRole;

import java.time.Instant;

/** An active role grant, as listed under a role or a user in admin screens. */
public record RoleGrantDto(
    Long id,
    Long userId,
    Long roleId,
    String username,
    String displayName,
    Long companyId,
    Long branchId,
    Instant grantedAt
) {
    public static RoleGrantDto from(UserRole grant, AppUser user) {
        return new RoleGrantDto(
            grant.getId(),
            grant.getUserId(),
            grant.getRoleId(),
            user != null ? user.getUsername() : null,
            user != null ? user.getDisplayName() : null,
            grant.getCompanyId(),
            grant.getBranchId(),
            grant.getGrantedAt()
        );
    }
}
