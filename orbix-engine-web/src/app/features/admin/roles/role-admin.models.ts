/** Mirrors the backend iam role-admin DTOs (see RoleAdminController). */

export interface Permission {
  id: number;
  code: string;
  description: string;
  module: string;
}

export interface RoleSummary {
  id: number;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  status: string;
  permissionCount: number;
}

export interface RoleDetail {
  id: number;
  code: string;
  name: string;
  description: string | null;
  isSystem: boolean;
  status: string;
  permissions: Permission[];
}

export interface RoleGrant {
  id: number;
  userId: number;
  username: string;
  displayName: string;
  companyId: number;
  branchId: number | null;
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
  branchId: number | null;
}
