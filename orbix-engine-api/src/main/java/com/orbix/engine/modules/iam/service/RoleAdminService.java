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

    RoleDetailDto getRoleByUid(String uid);

    RoleDetailDto createRole(CreateRoleRequestDto request);

    RoleDetailDto updateRoleByUid(String uid, UpdateRoleRequestDto request);

    RoleDetailDto setPermissionsByUid(String uid, SetRolePermissionsRequestDto request);

    /** Deletes a non-system role. Blocked if the role still has active grants. */
    void deleteRoleByUid(String uid);

    /** Active grants of this role, scoped to the caller's company. */
    List<RoleGrantDto> listGrantsByUid(String uid);

    RoleGrantDto grantRoleByUid(String uid, GrantRoleRequestDto request);

    void revokeGrantByUid(String grantUid);
}
