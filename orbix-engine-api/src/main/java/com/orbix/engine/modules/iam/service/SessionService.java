package com.orbix.engine.modules.iam.service;

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
     * Persists the user's active branch preference on {@code app_user}.
     * Throws {@link org.springframework.security.access.AccessDeniedException}
     * if the user has no grant for that branch.
     */
    void setActiveBranch(Long branchId);
}
