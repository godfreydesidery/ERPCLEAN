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

    BranchResponseDto getBranchByUid(String uid);

    BranchResponseDto createBranch(CreateBranchRequestDto request);

    BranchResponseDto updateBranchByUid(String uid, UpdateBranchRequestDto request);

    /** Marks the branch INACTIVE. Rejects an already-inactive branch and the default branch. */
    void deactivateBranchByUid(String uid, String reason);

    /** Marks the branch ACTIVE again. Rejects an already-active branch. */
    void activateBranchByUid(String uid, String reason);
}
