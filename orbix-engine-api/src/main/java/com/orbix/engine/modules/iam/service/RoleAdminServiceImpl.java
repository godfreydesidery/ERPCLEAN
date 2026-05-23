package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.TokenGuardService;
import com.orbix.engine.modules.iam.domain.dto.CreateRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.GrantRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.PermissionDto;
import com.orbix.engine.modules.iam.domain.dto.RoleDetailDto;
import com.orbix.engine.modules.iam.domain.dto.RoleGrantDto;
import com.orbix.engine.modules.iam.domain.dto.RoleSummaryDto;
import com.orbix.engine.modules.iam.domain.dto.SetRolePermissionsRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateRoleRequestDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.Permission;
import com.orbix.engine.modules.iam.domain.entity.Role;
import com.orbix.engine.modules.iam.domain.entity.UserRole;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.repository.PermissionRepository;
import com.orbix.engine.modules.iam.repository.RoleRepository;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleAdminServiceImpl implements RoleAdminService {

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final UserRoleRepository userRoles;
    private final AppUserRepository users;
    private final TokenGuardService tokenGuard;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<PermissionDto> listPermissions() {
        return permissions.findAll().stream()
            .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
            .map(PermissionDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleSummaryDto> listRoles() {
        return roles.findAll().stream()
            .sorted(Comparator.comparing(Role::getCode))
            .map(RoleSummaryDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDetailDto getRoleByUid(String uid) {
        return RoleDetailDto.from(requireRoleByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Role")
    public RoleDetailDto createRole(CreateRoleRequestDto request) {
        String code = request.code().trim().toUpperCase();
        if (roles.existsByCode(code)) {
            throw new IllegalArgumentException("Role code already exists: " + code);
        }
        Role role = new Role(code, request.name(), request.description(), false, context.userId());
        return RoleDetailDto.from(roles.save(role));
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Role")
    public RoleDetailDto updateRoleByUid(String uid, UpdateRoleRequestDto request) {
        Role role = requireMutableRoleByUid(uid);
        role.updateDetails(request.name(), request.description(), context.userId());
        return RoleDetailDto.from(roles.save(role));
    }

    @Override
    @Transactional
    @Auditable(action = "SET_PERMISSIONS", entityType = "Role")
    public RoleDetailDto setPermissionsByUid(String uid, SetRolePermissionsRequestDto request) {
        Role role = requireMutableRoleByUid(uid);
        List<Long> ids = request.permissionIds();
        Set<Permission> resolved = new HashSet<>(permissions.findAllById(ids));
        if (resolved.size() != new HashSet<>(ids).size()) {
            throw new IllegalArgumentException("One or more permission ids are invalid");
        }
        role.replacePermissions(resolved, context.userId());
        return RoleDetailDto.from(roles.save(role));
    }

    @Override
    @Transactional
    @Auditable(action = "DELETE", entityType = "Role")
    public void deleteRoleByUid(String uid) {
        Role role = requireMutableRoleByUid(uid);
        if (userRoles.existsByRoleIdAndRevokedAtIsNull(role.getId())) {
            throw new IllegalArgumentException("Role still has active grants — revoke them first");
        }
        roles.delete(role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleGrantDto> listGrantsByUid(String uid) {
        Role role = requireRoleByUid(uid);
        Long companyId = context.companyId();
        List<UserRole> grants = userRoles.findByRoleIdAndRevokedAtIsNull(role.getId()).stream()
            .filter(g -> Objects.equals(g.getCompanyId(), companyId))
            .toList();
        Map<Long, AppUser> usersById = users.findAllById(
                grants.stream().map(UserRole::getUserId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        return grants.stream()
            .map(g -> RoleGrantDto.from(g, usersById.get(g.getUserId())))
            .sorted(Comparator.comparing(RoleGrantDto::grantedAt).reversed())
            .toList();
    }

    @Override
    @Transactional
    @Auditable(action = "GRANT", entityType = "UserRole")
    public RoleGrantDto grantRoleByUid(String uid, GrantRoleRequestDto request) {
        Role role = requireRoleByUid(uid);
        Long companyId = context.companyId();
        AppUser user = users.findByUsername(request.username().trim())
            .orElseThrow(() -> new NoSuchElementException("User not found: " + request.username()));

        boolean alreadyGranted = userRoles
            .findByUserIdAndCompanyIdAndRevokedAtIsNull(user.getId(), companyId).stream()
            .anyMatch(g -> Objects.equals(g.getRoleId(), role.getId())
                && Objects.equals(g.getBranchId(), request.branchId()));
        if (alreadyGranted) {
            throw new IllegalArgumentException(
                "User already has role '" + role.getCode() + "' in this scope");
        }

        UserRole grant = new UserRole(user.getId(), role.getId(), companyId,
            request.branchId(), context.userId());
        return RoleGrantDto.from(userRoles.save(grant), user);
    }

    @Override
    @Transactional
    @Auditable(action = "REVOKE", entityType = "UserRole")
    public void revokeGrantByUid(String grantUid) {
        UserRole grant = userRoles.findByUid(grantUid)
            .orElseThrow(() -> new NoSuchElementException("Grant not found: " + grantUid));
        grant.revoke(Instant.now());
        userRoles.save(grant);
        // Force the affected user to re-mint a token so the lost permissions
        // drop out of their `perms` claim immediately, not at token expiry.
        tokenGuard.invalidateUserTokens(grant.getUserId());
    }

    private Role requireRoleByUid(String uid) {
        return roles.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Role not found: " + uid));
    }

    private Role requireMutableRoleByUid(String uid) {
        Role role = requireRoleByUid(uid);
        if (role.isSystem()) {
            throw new IllegalArgumentException("System role '" + role.getCode() + "' cannot be modified");
        }
        return role;
    }
}
