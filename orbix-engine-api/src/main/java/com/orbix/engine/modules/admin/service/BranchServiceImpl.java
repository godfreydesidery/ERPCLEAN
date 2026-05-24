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
import com.orbix.engine.modules.party.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branches;
    private final SectionRepository sections;
    private final CustomerService customerService;
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
    public BranchResponseDto getBranchByUid(String uid) {
        return BranchResponseDto.from(requireBranchByUid(uid));
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
        validateZone(request.timeZone());

        Branch branch = branches.save(new Branch(
            companyId, code, request.name(), request.type(),
            request.timeZone(), false, actorId));
        branch.setPhysicalAddress(request.physicalAddress());
        branch.setPhone(request.phone());

        // Branches that trade with customers get a default sales floor to trade
        // from plus a synthetic walk-in customer for POS (F1.7 hook). Storage /
        // office branches (warehouse, head office) get neither — they only hold
        // and transfer stock.
        if (request.type().sellsToCustomers()) {
            sections.save(new Section(branch.getId(), "MAIN", "Main Floor",
                SectionType.RETAIL_FLOOR, actorId));
            customerService.createWalkInCustomer(branch.getId());
        }

        events.publish("BranchCreated.v1", "Branch", branch.getUid(),
            Map.of(BRANCH_UID_KEY, branch.getUid(), "companyId", companyId, "code", code));
        return BranchResponseDto.from(branch);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Branch")
    public BranchResponseDto updateBranchByUid(String uid, UpdateBranchRequestDto request) {
        Branch branch = requireBranchByUid(uid);
        validateZone(request.timeZone());
        branch.updateDetails(request.name(), request.type(), request.physicalAddress(),
            request.phone(), request.timeZone(), context.userId());
        events.publish("BranchUpdated.v1", "Branch", branch.getUid(),
            Map.of(BRANCH_UID_KEY, branch.getUid()));
        return BranchResponseDto.from(branch);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Branch")
    public void deactivateBranchByUid(String uid, String reason) {
        Branch branch = requireBranchByUid(uid);
        if (branch.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Branch is already inactive: " + uid);
        }
        if (branch.isDefault()) {
            throw new IllegalArgumentException("Cannot deactivate the default branch");
        }
        // TODO (F5.1): block deactivation while the branch has an open till.
        branch.deactivate(context.userId());
        events.publish("BranchDeactivated.v1", "Branch", branch.getUid(),
            Map.of(BRANCH_UID_KEY, branch.getUid(), "reason", reason));
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Branch")
    public void activateBranchByUid(String uid, String reason) {
        Branch branch = requireBranchByUid(uid);
        if (branch.getStatus() == AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Branch is already active: " + uid);
        }
        branch.activate(context.userId());
        events.publish("BranchActivated.v1", "Branch", branch.getUid(),
            Map.of(BRANCH_UID_KEY, branch.getUid(), "reason", reason));
    }

    private static void validateZone(String timeZone) {
        try {
            ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid time zone: " + timeZone);
        }
    }

    private Branch requireBranchByUid(String uid) {
        Branch branch = branches.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + uid));
        if (!branch.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Branch not found: " + uid);
        }
        return branch;
    }

    private static final String BRANCH_UID_KEY = "branchUid";
}
