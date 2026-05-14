package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.BranchResponseDto;
import com.orbix.engine.modules.admin.domain.dto.CreateBranchRequestDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateBranchRequestDto;
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
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branches;
    private final SectionRepository sections;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<BranchResponseDto> listBranches() {
        return branches.findByCompanyId(context.companyId()).stream()
            .sorted(Comparator.comparing(Branch::getCode))
            .map(BranchResponseDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public BranchResponseDto getBranch(Long branchId) {
        return BranchResponseDto.from(requireBranch(branchId));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Branch")
    public BranchResponseDto createBranch(CreateBranchRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        String code = request.code().trim().toUpperCase();

        if (branches.existsByCompanyIdAndCode(companyId, code)) {
            throw new IllegalArgumentException("Branch code already exists: " + code);
        }

        Branch branch = branches.save(new Branch(
            companyId, code, request.name(), request.type(),
            request.timeZone(), false, actorId));
        branch.setPhysicalAddress(request.physicalAddress());
        branch.setPhone(request.phone());

        // Every branch needs at least one RETAIL_FLOOR section to trade from.
        sections.save(new Section(branch.getId(), "MAIN", "Main Floor",
            SectionType.RETAIL_FLOOR, actorId));

        events.publish("BranchCreated.v1", "Branch", String.valueOf(branch.getId()),
            Map.of("branchId", branch.getId(), "companyId", companyId, "code", code));
        return BranchResponseDto.from(branch);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Branch")
    public BranchResponseDto updateBranch(Long branchId, UpdateBranchRequestDto request) {
        Branch branch = requireBranch(branchId);
        branch.updateDetails(request.name(), request.type(), request.physicalAddress(),
            request.phone(), request.timeZone(), context.userId());
        events.publish("BranchUpdated.v1", "Branch", String.valueOf(branch.getId()),
            Map.of("branchId", branch.getId()));
        return BranchResponseDto.from(branch);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Branch")
    public void deactivateBranch(Long branchId) {
        Branch branch = requireBranch(branchId);
        if (branch.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Branch is already inactive: " + branchId);
        }
        // TODO (F5.1): block deactivation while the branch has an open till.
        branch.deactivate(context.userId());
        events.publish("BranchDeactivated.v1", "Branch", String.valueOf(branch.getId()),
            Map.of("branchId", branch.getId()));
    }

    private Branch requireBranch(Long branchId) {
        Branch branch = branches.findById(branchId)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchId));
        if (!branch.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Branch not found: " + branchId);
        }
        return branch;
    }
}
