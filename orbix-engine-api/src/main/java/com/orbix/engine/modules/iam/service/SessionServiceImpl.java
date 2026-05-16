package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.auth.service.AuthService;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.domain.dto.AccessibleBranchDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.UserRole;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final UserRoleRepository userRoles;
    private final BranchRepository branches;
    private final AppUserRepository users;
    private final BranchAccessGuard branchAccessGuard;
    private final AuthService authService;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<AccessibleBranchDto> listAccessibleBranches() {
        Long userId = context.userId();
        Long companyId = context.companyId();

        List<UserRole> grants = userRoles.findByUserIdAndCompanyIdAndRevokedAtIsNull(userId, companyId);
        List<Branch> activeBranches = branches.findByCompanyId(companyId).stream()
            .filter(b -> b.getStatus() == AdminStatus.ACTIVE)
            .toList();

        boolean companyWide = grants.stream().anyMatch(g -> g.getBranchId() == null);
        if (!companyWide) {
            Set<Long> grantedBranchIds = grants.stream()
                .map(UserRole::getBranchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            activeBranches = activeBranches.stream()
                .filter(b -> grantedBranchIds.contains(b.getId()))
                .toList();
        }

        return activeBranches.stream()
            .sorted(Comparator.comparing(Branch::getCode))
            .map(AccessibleBranchDto::from)
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "SET_ACTIVE_BRANCH", entityType = "AppUser")
    public LoginResponseDto setActiveBranch(Long branchId) {
        Long userId = context.userId();
        Long companyId = context.companyId();

        Branch branch = branches.findById(branchId)
            .orElseThrow(() -> new NoSuchElementException("Branch not found: " + branchId));
        if (!Objects.equals(branch.getCompanyId(), companyId)) {
            throw new AccessDeniedException("Branch " + branchId + " is not in your company");
        }
        branchAccessGuard.verify(userId, companyId, branchId);

        AppUser user = users.findById(userId)
            .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
        user.setDefaultBranchId(branchId);
        user.setUpdatedAt(Instant.now());
        user.setUpdatedBy(userId);
        users.save(user);

        // Reissue tokens so the new JWT carries (branchId, perms[]) freshly
        // resolved against the new branch context — no more X-Branch-Id
        // override path on subsequent calls.
        return authService.reissueTokens(userId);
    }
}
