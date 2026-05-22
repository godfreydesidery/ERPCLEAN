package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.enums.AppUserStatus;

import java.time.Instant;
import java.util.List;

/**
 * Full projection of an {@code app_user} including their active role grants
 * within the current company.
 */
public record UserDetailDto(
    Long id,
    String username,
    String displayName,
    String email,
    String phone,
    Long defaultBranchId,
    AppUserStatus status,
    boolean locked,
    Instant lockedUntil,
    boolean mustChangePassword,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt,
    List<RoleGrantDto> grants
) {
    public static UserDetailDto from(AppUser u, List<RoleGrantDto> grants) {
        Instant now = Instant.now();
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
        return new UserDetailDto(
            u.getId(),
            u.getUsername(),
            u.getDisplayName(),
            u.getEmail(),
            u.getPhone(),
            u.getDefaultBranchId(),
            u.getStatus(),
            locked,
            u.getLockedUntil(),
            u.isMustChangePassword(),
            u.getLastLoginAt(),
            u.getCreatedAt(),
            u.getUpdatedAt(),
            grants
        );
    }
}
