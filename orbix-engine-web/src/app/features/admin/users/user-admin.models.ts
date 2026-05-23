/** Mirrors the backend `app_user` admin DTOs. */

export type AppUserStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED' | 'SUSPENDED';

export interface UserSummary {
  id: string;
  uid: string;
  username: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  defaultBranchId: string | null;
  status: AppUserStatus;
  locked: boolean;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface RoleGrantSummary {
  id: string;
  uid: string;
  userId: string;
  roleId: string;
  username: string | null;
  displayName: string | null;
  companyId: string;
  branchId: string | null;
  grantedAt: string;
}

export interface UserDetail {
  id: string;
  uid: string;
  username: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  defaultBranchId: string | null;
  status: AppUserStatus;
  locked: boolean;
  lockedUntil: string | null;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
  grants: RoleGrantSummary[];
}

export interface CreateUserRequest {
  username: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  defaultBranchId: string | null;
  password: string | null;
  mustChangePassword: boolean | null;
}

export interface CreateUserResponse {
  user: UserDetail;
  temporaryPassword: string | null;
}

export interface UpdateUserRequest {
  displayName: string;
  email: string | null;
  phone: string | null;
  defaultBranchId: string | null;
}

export interface ResetPasswordRequest {
  newPassword: string | null;
  mustChangePassword: boolean | null;
}

export interface ResetPasswordResponse {
  user: UserDetail;
  temporaryPassword: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
