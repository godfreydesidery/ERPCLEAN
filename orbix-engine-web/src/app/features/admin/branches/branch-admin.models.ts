/** Mirrors the backend admin branch/section DTOs (see BranchController, SectionController). */

export interface Branch {
  id: number;
  companyId: number;
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
  id: number;
  branchId: number;
  code: string;
  name: string;
  type: string;
  managerUserId: number | null;
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
  managerUserId: number | null;
}

export interface UpdateSectionRequest {
  name: string;
  type: string;
  managerUserId: number | null;
}

export const SECTION_TYPES = [
  'RETAIL_FLOOR', 'BAKERY', 'BUTCHERY', 'DELI', 'FRESH',
  'DAIRY', 'DRY_GOODS', 'HOUSEHOLD', 'ELECTRONICS', 'OTHER'
] as const;
