package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.iam.domain.dto.CreateRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.GrantRoleRequestDto;
import com.orbix.engine.modules.iam.domain.dto.PermissionDto;
import com.orbix.engine.modules.iam.domain.dto.RoleDetailDto;
import com.orbix.engine.modules.iam.domain.dto.RoleGrantDto;
import com.orbix.engine.modules.iam.domain.dto.RoleSummaryDto;
import com.orbix.engine.modules.iam.domain.dto.SetRolePermissionsRequestDto;
import com.orbix.engine.modules.iam.domain.dto.UpdateRoleRequestDto;

import java.util.List;

/**
 * Admin operations behind the {@code IAM.MANAGE_ROLES} permission: role CRUD,
 * permission assignment, and granting / revoking roles to users. Backs the web
 * RoleAdminComponent.
 */
public interface RoleAdminService {

    /** Every grantable permission, for the role-edit permission picker. */
    List<PermissionDto> listPermissions();

    List<RoleSummaryDto> listRoles();

    RoleDetailDto getRole(Long roleId);

    RoleDetailDto createRole(CreateRoleRequestDto request);

    RoleDetailDto updateRole(Long roleId, UpdateRoleRequestDto request);

    RoleDetailDto setPermissions(Long roleId, SetRolePermissionsRequestDto request);

    /** Deletes a non-system role. Blocked if the role still has active grants. */
    void deleteRole(Long roleId);

    /** Active grants of this role, scoped to the caller's company. */
    List<RoleGrantDto> listGrants(Long roleId);

    RoleGrantDto grantRole(Long roleId, GrantRoleRequestDto request);

    void revokeGrant(Long grantId);
}
