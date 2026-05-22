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
    public List<SectionResponseDto> listSections(Long branchId) {
        requireBranch(branchId);
        return sections.findByBranchId(branchId).stream()
            .sorted(Comparator.comparing(Section::getCode))
            .map(SectionResponseDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Section")
    public SectionResponseDto createSection(Long branchId, CreateSectionRequestDto request) {
        Branch branch = requireBranch(branchId);
        if (branch.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Cannot add a section to an inactive branch");
        }
        String code = request.code().trim().toUpperCase();
        if (sections.existsByBranchIdAndCode(branchId, code)) {
            throw new IllegalArgumentException("Section code already exists in this branch: " + code);
        }

        Section section = new Section(branchId, code, request.name(), request.type(), context.userId());
        section.setManagerUserId(request.managerUserId());
        section = sections.save(section);

        events.publish("SectionCreated.v1", "Section", String.valueOf(section.getId()),
            Map.of("sectionId", section.getId(), "branchId", branchId, "code", code));
        return SectionResponseDto.from(section);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Section")
    public SectionResponseDto updateSection(Long sectionId, UpdateSectionRequestDto request) {
        Section section = requireSection(sectionId);
        section.updateDetails(request.name(), request.type(), request.managerUserId(), context.userId());
        events.publish("SectionUpdated.v1", "Section", String.valueOf(section.getId()),
            Map.of("sectionId", section.getId()));
        return SectionResponseDto.from(section);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Section")
    public void deactivateSection(Long sectionId) {
        Section section = requireSection(sectionId);
        if (section.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Section is already inactive: " + sectionId);
        }
        if (section.getType() == SectionType.RETAIL_FLOOR && isLastActiveRetailFloor(section)) {
            throw new IllegalArgumentException(
                "Cannot deactivate the branch's last active RETAIL_FLOOR section");
        }
        // TODO (F5.1 / F7.3): block deactivation while the section has active tills or BOMs.
        section.deactivate(context.userId());
        events.publish("SectionDeactivated.v1", "Section", String.valueOf(section.getId()),
            Map.of("sectionId", section.getId()));
    }

    private boolean isLastActiveRetailFloor(Section section) {
        return sections.findByBranchId(section.getBranchId()).stream()
            .filter(s -> !s.getId().equals(section.getId()))
            .filter(s -> s.getStatus() == AdminStatus.ACTIVE)
            .noneMatch(s -> s.getType() == SectionType.RETAIL_FLOOR);
    }

    private Branch requireBranch(Long branchId) {
        Branch branch = branches.findById(branchId)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchId));
        if (!branch.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Branch not found: " + branchId);
        }
        return branch;
    }

    private Section requireSection(Long sectionId) {
        Section section = sections.findById(sectionId)
            .orElseThrow(() -> new NoSuchElementException("Section not found: " + sectionId));
        requireBranch(section.getBranchId());
        return section;
    }
}
