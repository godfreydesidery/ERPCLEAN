/** Mirrors the backend admin branch/section DTOs (see BranchController, SectionController). */

export interface Branch {
  id: string;
  uid: string;
  companyId: string;
  code: string;
  name: string;
  type: string;
  physicalAddress: string | null;
  phone: string | null;
  timeZone: string;
  isDefault: boolean;
  status: string;
}

export interface Section {
  id: string;
  uid: string;
  branchId: string;
  code: string;
  name: string;
  type: string;
  managerUserId: string | null;
  status: string;
}

export interface CreateBranchRequest {
  code: string;
  name: string;
  type: string;
  physicalAddress: string | null;
  phone: string | null;
  timeZone: string;
}

export interface UpdateBranchRequest {
  name: string;
  type: string;
  physicalAddress: string | null;
  phone: string | null;
  timeZone: string;
}

export interface CreateSectionRequest {
  code: string;
  name: string;
  type: string;
  managerUserId: string | null;
}

export interface UpdateSectionRequest {
  name: string;
  type: string;
  managerUserId: string | null;
}

export const SECTION_TYPES = [
  'RETAIL_FLOOR', 'BAKERY', 'BUTCHERY', 'DELI', 'FRESH',
  'DAIRY', 'DRY_GOODS', 'HOUSEHOLD', 'ELECTRONICS', 'OTHER'
] as const;
