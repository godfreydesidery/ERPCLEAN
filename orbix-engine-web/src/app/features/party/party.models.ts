/** Mirrors the backend party DTOs (Customer/Supplier/Employee/SalesAgent controllers). */

export type PartyCategory = 'INDIVIDUAL' | 'BUSINESS' | 'GOVERNMENT' | 'NGO';
export type PartyStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export const PARTY_CATEGORIES: PartyCategory[] = ['INDIVIDUAL', 'BUSINESS', 'GOVERNMENT', 'NGO'];

/** Editable party-level fields shared by every role's create / update payload. */
export interface PartyDetails {
  name: string;
  legalName: string | null;
  category: PartyCategory;
  tin: string | null;
  vrn: string | null;
  phone: string | null;
  email: string | null;
  physicalAddress: string | null;
  postalAddress: string | null;
  countryCode: string | null;
  notes: string | null;
}

export interface PartyResponse {
  id: number;
  companyId: number;
  code: string;
  name: string;
  legalName: string | null;
  category: PartyCategory;
  tin: string | null;
  vrn: string | null;
  phone: string | null;
  email: string | null;
  physicalAddress: string | null;
  postalAddress: string | null;
  countryCode: string | null;
  notes: string | null;
  status: PartyStatus;
}

export interface Customer {
  partyId: number;
  party: PartyResponse;
  creditLimitAmount: number;
  creditTermsDays: number;
  priceListId: number | null;
  defaultSalesAgentId: number | null;
  defaultBranchId: number | null;
  walkIn: boolean;
  taxExempt: boolean;
}

export interface CreateCustomerRequest {
  code: string;
  party: PartyDetails;
  creditLimitAmount: number;
  creditTermsDays: number;
  priceListId: number | null;
  defaultSalesAgentId: number | null;
  defaultBranchId: number | null;
  taxExempt: boolean;
}

export interface Supplier {
  partyId: number;
  party: PartyResponse;
  paymentTermsDays: number;
  creditLimitAmount: number;
  defaultCurrencyCode: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  leadTimeDays: number | null;
}

export interface CreateSupplierRequest {
  code: string;
  party: PartyDetails;
  paymentTermsDays: number;
  creditLimitAmount: number;
  defaultCurrencyCode: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  leadTimeDays: number | null;
}

export interface Employee {
  partyId: number;
  party: PartyResponse;
  appUserId: number | null;
  employeeCode: string;
  jobTitle: string | null;
  branchId: number;
  hireDate: string | null;
  terminationDate: string | null;
}

export interface CreateEmployeeRequest {
  code: string;
  party: PartyDetails;
  employeeCode: string;
  appUserId: number | null;
  jobTitle: string | null;
  branchId: number;
  hireDate: string | null;
  terminationDate: string | null;
}

export interface SalesAgent {
  partyId: number;
  party: PartyResponse;
  appUserId: number | null;
  agentCode: string;
  routeId: number | null;
  commissionRate: number | null;
  branchId: number;
}

export interface CreateSalesAgentRequest {
  /** Set to promote an existing party into the sales-agent role. */
  partyId: number | null;
  /** Required when {@link partyId} is null — code for the new party. */
  code: string | null;
  /** Required when {@link partyId} is null — details of the new party. */
  party: PartyDetails | null;
  agentCode: string;
  appUserId: number | null;
  routeId: number | null;
  commissionRate: number | null;
  branchId: number;
}

export function blankPartyDetails(): PartyDetails {
  return {
    name: '', legalName: null, category: 'BUSINESS', tin: null, vrn: null,
    phone: null, email: null, physicalAddress: null, postalAddress: null,
    countryCode: null, notes: null
  };
}
