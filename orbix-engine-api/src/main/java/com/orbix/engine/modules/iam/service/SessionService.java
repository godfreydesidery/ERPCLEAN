package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.iam.domain.dto.AccessibleBranchDto;

import java.util.List;

/**
 * Per-user session context operations: which branches the caller may work in,
 * and switching their active branch. Backs the app-shell branch picker (F0.5).
 */
public interface SessionService {

    /** Active branches the current user holds a role grant for, in their company. */
    List<AccessibleBranchDto> listAccessibleBranches();

    /**
     * Persists the user's active branch preference on {@code app_user} and
     * returns a fresh access + refresh token pair whose JWT carries the new
     * {@code branchId} claim plus {@code perms[]} resolved against the new
     * branch context. The frontend stores the new pair so subsequent calls
     * don't depend on the {@code X-Branch-Id} override path.
     *
     * @throws org.springframework.security.access.AccessDeniedException
     *         if the user has no role grant covering that branch.
     */
    LoginResponseDto setActiveBranch(Long branchId);
}
