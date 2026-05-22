package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.dto.SectionResponseDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

    private final SectionRepository sections;
    private final BranchRepository branches;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<SectionResponseDto> listSectionsByBranchUid(String branchUid) {
        Branch branch = requireBranchByUid(branchUid);
        return sections.findByBranchId(branch.getId()).stream()
            .sorted(Comparator.comparing(Section::getCode))
            .map(SectionResponseDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Section")
    public SectionResponseDto createSectionByBranchUid(String branchUid, CreateSectionRequestDto request) {
        Branch branch = requireBranchByUid(branchUid);
        if (branch.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot add a section to an inactive branch");
        }
        Long branchId = branch.getId();
        String code = request.code().trim().toUpperCase();
        if (sections.existsByBranchIdAndCode(branchId, code)) {
            throw new IllegalArgumentException("Section code already exists in this branch: " + code);
        }

        Section section = new Section(branchId, code, request.name(), request.type(), context.userId());
        section.setManagerUserId(request.managerUserId());
        section = sections.save(section);

        events.publish("SectionCreated.v1", "Section", section.getUid(),
            Map.of(SECTION_UID_KEY, section.getUid(), "branchUid", branchUid, "code", code));
        return SectionResponseDto.from(section);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Section")
    public SectionResponseDto updateSectionByUid(String uid, UpdateSectionRequestDto request) {
        Section section = requireSectionByUid(uid);
        section.updateDetails(request.name(), request.type(), request.managerUserId(), context.userId());
        events.publish("SectionUpdated.v1", "Section", section.getUid(),
            Map.of(SECTION_UID_KEY, section.getUid()));
        return SectionResponseDto.from(section);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Section")
    public void deactivateSectionByUid(String uid) {
        Section section = requireSectionByUid(uid);
        if (section.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Section is already inactive: " + uid);
        }
        if (section.getType() == SectionType.RETAIL_FLOOR && isLastActiveRetailFloor(section)) {
            throw new IllegalArgumentException(
                "Cannot deactivate the branch's last active RETAIL_FLOOR section");
        }
        // TODO (F5.1 / F7.3): block deactivation while the section has active tills or BOMs.
        section.deactivate(context.userId());
        events.publish("SectionDeactivated.v1", "Section", section.getUid(),
            Map.of(SECTION_UID_KEY, section.getUid()));
    }

    private boolean isLastActiveRetailFloor(Section section) {
        return sections.findByBranchId(section.getBranchId()).stream()
            .filter(s -> !s.getId().equals(section.getId()))
            .filter(s -> s.getStatus() == AdminStatus.ACTIVE)
            .noneMatch(s -> s.getType() == SectionType.RETAIL_FLOOR);
    }

    private Branch requireBranchByUid(String branchUid) {
        Branch branch = branches.findByUid(branchUid)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchUid));
        if (!branch.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Branch not found: " + branchUid);
        }
        return branch;
    }

    private Branch requireBranchById(Long branchId) {
        Branch branch = branches.findById(branchId)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchId));
        if (!branch.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Branch not found: " + branchId);
        }
        return branch;
    }

    private Section requireSectionByUid(String uid) {
        Section section = sections.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Section not found: " + uid));
        requireBranchById(section.getBranchId());
        return section;
    }

    private static final String SECTION_UID_KEY = "sectionUid";
}
