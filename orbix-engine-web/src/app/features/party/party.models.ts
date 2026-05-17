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
  id: string;
  uid: string;
  companyId: string;
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
  partyId: string;
  party: PartyResponse;
  creditLimitAmount: number;
  creditTermsDays: number;
  priceListId: string | null;
  defaultSalesAgentId: string | null;
  defaultBranchId: string | null;
  walkIn: boolean;
  taxExempt: boolean;
}

export interface CreateCustomerRequest {
  partyId: string | null;
  party: PartyDetails | null;
  creditLimitAmount: number;
  creditTermsDays: number;
  priceListId: string | null;
  defaultSalesAgentId: string | null;
  defaultBranchId: string | null;
  taxExempt: boolean;
}

export interface UpdateCustomerRequest {
  party: PartyDetails;
  creditLimitAmount: number;
  creditTermsDays: number;
  priceListId: string | null;
  defaultSalesAgentId: string | null;
  defaultBranchId: string | null;
  taxExempt: boolean;
}

export interface Supplier {
  partyId: string;
  party: PartyResponse;
  paymentTermsDays: number;
  creditLimitAmount: number;
  defaultCurrencyCode: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  leadTimeDays: number | null;
}

export interface CreateSupplierRequest {
  partyId: string | null;
  party: PartyDetails | null;
  paymentTermsDays: number;
  creditLimitAmount: number;
  defaultCurrencyCode: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  leadTimeDays: number | null;
}

export interface UpdateSupplierRequest {
  party: PartyDetails;
  paymentTermsDays: number;
  creditLimitAmount: number;
  defaultCurrencyCode: string | null;
  bankName: string | null;
  bankAccountNo: string | null;
  leadTimeDays: number | null;
}

export interface Employee {
  partyId: string;
  party: PartyResponse;
  appUserId: string | null;
  employeeCode: string;
  jobTitle: string | null;
  branchId: string;
  hireDate: string | null;
  terminationDate: string | null;
}

export interface CreateEmployeeRequest {
  partyId: string | null;
  party: PartyDetails | null;
  employeeCode: string;
  appUserId: string | null;
  jobTitle: string | null;
  branchId: string;
  hireDate: string | null;
  terminationDate: string | null;
}

export interface UpdateEmployeeRequest {
  party: PartyDetails;
  appUserId: string | null;
  jobTitle: string | null;
  branchId: string;
  hireDate: string | null;
  terminationDate: string | null;
}

export interface SalesAgent {
  partyId: string;
  party: PartyResponse;
  appUserId: string | null;
  agentCode: string;
  routeId: string | null;
  commissionRate: number | null;
  branchId: string;
}

export interface CreateSalesAgentRequest {
  /** Set to promote an existing party into the sales-agent role. */
  partyId: string | null;
  /**
   * Required when {@link partyId} is null — details of the new party. The
   * backend allocates the party code from the AGT sequence; clients cannot
   * supply one.
   */
  party: PartyDetails | null;
  agentCode: string;
  appUserId: string | null;
  routeId: string | null;
  commissionRate: number | null;
  branchId: string;
}

export interface UpdateSalesAgentRequest {
  party: PartyDetails;
  appUserId: string | null;
  routeId: string | null;
  commissionRate: number | null;
  branchId: string;
}

export function blankPartyDetails(): PartyDetails {
  return {
    name: '', legalName: null, category: 'BUSINESS', tin: null, vrn: null,
    phone: null, email: null, physicalAddress: null, postalAddress: null,
    countryCode: null, notes: null
  };
}
