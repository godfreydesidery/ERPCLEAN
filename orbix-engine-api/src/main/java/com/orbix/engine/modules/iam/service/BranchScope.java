package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Server-side branch scoping for read endpoints. Every service whose list
 * query accepts a user-supplied {@code branchId} (or whose
 * {@code GET /{id}} returns a row carrying a {@code branch_id}) should run
 * the value through here before trusting it.
 *
 * <p>This complements {@link BranchAccessGuard}, which already gates the
 * {@code X-Branch-Id} override header at the filter layer. The header
 * path is covered; this helper closes the gap for query-param /
 * path-param branch IDs.
 *
 * <h2>Semantics</h2>
 * <dl>
 *   <dt>{@link #requireReadable(Long)}</dt>
 *   <dd>For list endpoints. Null in: null out IFF caller holds at least
 *       one company-wide grant; otherwise rewritten to
 *       {@code context.branchId()} so a branch-scoped user can't accidentally
 *       (or maliciously) see other branches by omitting the param.
 *       Non-null in: verified against the caller's grants — 403 if no match.</dd>
 *   <dt>{@link #requireAccess(Long)}</dt>
 *   <dd>For single-resource lookups. Pass the row's {@code branchId} after
 *       loading it; throws 403 if the caller has no grant covering that
 *       branch.</dd>
 * </dl>
 */
@Component
@RequiredArgsConstructor
public class BranchScope {

    private final RequestContext context;
    private final UserRoleRepository userRoles;

    /**
     * Validate (and possibly rewrite) a user-supplied branch filter for a
     * list query. See class javadoc for semantics.
     *
     * @param requestedBranchId branchId from the query / path param; may be null
     * @return the branchId the service should actually filter by — null means
     *         "no branch filter" (company-wide caller only)
     */
    public Long requireReadable(Long requestedBranchId) {
        Long userId = context.userId();
        Long companyId = context.companyId();
        if (requestedBranchId == null) {
            return userRoles.hasAnyCompanyWideGrant(userId, companyId)
                ? null
                : context.branchId();
        }
        if (!userRoles.hasBranchAccess(userId, companyId, requestedBranchId)) {
            throw new AccessDeniedException(
                "No role grant for branch " + requestedBranchId);
        }
        return requestedBranchId;
    }

    /**
     * Verify the caller has access to a given branch. Pass the loaded
     * resource's {@code branchId} — throws 403 if denied.
     */
    public void requireAccess(Long branchId) {
        if (branchId == null) return;  // resource isn't branch-scoped
        Long userId = context.userId();
        Long companyId = context.companyId();
        if (!userRoles.hasBranchAccess(userId, companyId, branchId)) {
            throw new AccessDeniedException(
                "No role grant for branch " + branchId);
        }
    }
}
