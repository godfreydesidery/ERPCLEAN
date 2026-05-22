package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.BranchResponseDto;
import com.orbix.engine.modules.admin.domain.dto.CreateBranchRequestDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateBranchRequestDto;

import java.util.List;

/**
 * Branch management within the caller's company (F1.1). Creating a branch also
 * provisions its default RETAIL_FLOOR section.
 */
public interface BranchService {

    List<BranchResponseDto> listBranches();

    BranchResponseDto getBranch(Long branchId);

    BranchResponseDto createBranch(CreateBranchRequestDto request);

    BranchResponseDto updateBranch(Long branchId, UpdateBranchRequestDto request);

    /** Marks the branch INACTIVE. Idempotency: rejects an already-inactive branch. */
    void deactivateBranch(Long branchId);
}
