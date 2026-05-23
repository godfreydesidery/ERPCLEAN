/** Mirrors the backend CompanyDto / UpdateCompanyRequestDto (CompanyController). */

export interface Company {
  id: string;
  code: string;
  name: string;
  legalName: string | null;
  tin: string | null;
  vrn: string | null;
  physicalAddress: string | null;
  postalAddress: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  currencyCode: string;
  countryCode: string;
  timeZone: string;
  defaultInvoiceNote: string | null;
  defaultQuotationNote: string | null;
  logoObjectKey: string | null;
  status: string;
}

export interface UpdateCompanyRequest {
  name: string;
  legalName: string | null;
  tin: string | null;
  vrn: string | null;
  physicalAddress: string | null;
  postalAddress: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  currencyCode: string;
  countryCode: string;
  timeZone: string;
  defaultInvoiceNote: string | null;
  defaultQuotationNote: string | null;
}
