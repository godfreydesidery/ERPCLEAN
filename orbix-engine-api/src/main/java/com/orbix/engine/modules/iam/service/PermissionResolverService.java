package com.orbix.engine.modules.iam.service;

import java.util.Set;

/**
 * Looks up the union of permission codes granted to a user via
 * {@code user_role} -> {@code role_permission} -> {@code permission}.
 * Consumed by AuthService at login / refresh time to populate the JWT
 * {@code perms} claim.
 */
public interface PermissionResolverService {

    /**
     * @param userId    required
     * @param companyId required (every grant is company-scoped)
     * @param branchId  optional — branch-scoped grants are filtered; grants
     *                  with {@code branch_id = null} apply company-wide
     * @return distinct permission codes; empty set if the user has no role
     *         grants in the requested scope
     */
    Set<String> resolve(Long userId, Long companyId, Long branchId);
}
