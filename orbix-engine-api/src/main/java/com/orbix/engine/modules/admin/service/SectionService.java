package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.dto.SectionResponseDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateSectionRequestDto;

import java.util.List;

/**
 * Section management within a branch (F1.1). A branch must always retain at
 * least one active RETAIL_FLOOR section.
 */
public interface SectionService {

    List<SectionResponseDto> listSectionsByBranchUid(String branchUid);

    SectionResponseDto createSectionByBranchUid(String branchUid, CreateSectionRequestDto request);

    SectionResponseDto updateSectionByUid(String uid, UpdateSectionRequestDto request);

    /**
     * Marks the section INACTIVE. Rejected if it is the branch's last active
     * RETAIL_FLOOR section, or if the section is already inactive.
     */
    void deactivateSectionByUid(String uid);
}
