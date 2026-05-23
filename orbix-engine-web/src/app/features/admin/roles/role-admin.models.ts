/** Mirrors the backend iam role-admin DTOs (see RoleAdminController). */

export interface Permission {
  id: string;
  code: string;
  description: string;
  module: string;
}

export interface RoleSummary {
  id: string;
  uid: string;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  status: string;
  permissionCount: number;
}

export interface RoleDetail {
  id: string;
  uid: string;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  status: string;
  permissions: Permission[];
}

export interface RoleGrant {
  id: string;
  uid: string;
  userId: string;
  roleId: string;
  username: string;
  displayName: string;
  companyId: string;
  branchId: string | null;
  grantedAt: string;
}

export interface CreateRoleRequest {
  code: string;
  name: string;
  description: string;
}

export interface UpdateRoleRequest {
  name: string;
  description: string;
}

export interface GrantRoleRequest {
  username: string;
  branchId: string | null;
}
