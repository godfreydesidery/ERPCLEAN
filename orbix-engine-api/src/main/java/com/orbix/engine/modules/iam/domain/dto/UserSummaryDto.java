package com.orbix.engine.modules.iam.domain.dto;

import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.enums.AppUserStatus;

import java.time.Instant;

/**
 * Summary projection of an {@code app_user} for admin listings. Excludes
 * the password hash and any secret state.
 */
public record UserSummaryDto(
    Long id,
    String uid,
    String username,
    String displayName,
    String email,
    String phone,
    Long defaultBranchId,
    AppUserStatus status,
    boolean locked,
    boolean mustChangePassword,
    Instant lastLoginAt,
    Instant createdAt
) {
    public static UserSummaryDto from(AppUser u) {
        Instant now = Instant.now();
        boolean locked = u.getLockedUntil() != null && u.getLockedUntil().isAfter(now);
        return new UserSummaryDto(
            u.getId(),
            u.getUid(),
            u.getUsername(),
            u.getDisplayName(),
            u.getEmail(),
            u.getPhone(),
            u.getDefaultBranchId(),
            u.getStatus(),
            locked,
            u.isMustChangePassword(),
            u.getLastLoginAt(),
            u.getCreatedAt()
        );
    }
}
