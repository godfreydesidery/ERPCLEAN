package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Verifies that a user may operate within a given branch. A user has branch
 * access if they hold an active role grant covering that branch — either a
 * branch-specific grant or a company-wide one.
 *
 * <p>Consumed by {@code JwtAuthenticationFilter} when a request carries an
 * {@code X-Branch-Id} override, and by {@code SessionService} when switching
 * the active branch. Throws {@link AccessDeniedException} (mapped to 403) when
 * access is denied.
 */
@Component
@RequiredArgsConstructor
public class BranchAccessGuard {

    private final UserRoleRepository userRoles;

    public void verify(Long userId, Long companyId, Long branchId) {
        if (userId == null || companyId == null || branchId == null) {
            throw new AccessDeniedException("Branch context is incomplete");
        }
        if (!userRoles.hasBranchAccess(userId, companyId, branchId)) {
            throw new AccessDeniedException(
                "User " + userId + " has no role grant for branch " + branchId);
        }
    }
}
