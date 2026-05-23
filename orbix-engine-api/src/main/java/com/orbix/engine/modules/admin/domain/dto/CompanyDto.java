package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;

/** The current company's profile (US-COMP-002). {@code id} serialises as a string. */
public record CompanyDto(
    Long id,
    String code,
    String name,
    String legalName,
    String tin,
    String vrn,
    String physicalAddress,
    String postalAddress,
    String phone,
    String email,
    String website,
    String currencyCode,
    String countryCode,
    String timeZone,
    String defaultInvoiceNote,
    String defaultQuotationNote,
    String logoObjectKey,
    AdminStatus status
) {
    public static CompanyDto from(Company c) {
        return new CompanyDto(
            c.getId(), c.getCode(), c.getName(), c.getLegalName(), c.getTin(), c.getVrn(),
            c.getPhysicalAddress(), c.getPostalAddress(), c.getPhone(), c.getEmail(), c.getWebsite(),
            c.getCurrencyCode(), c.getCountryCode(), c.getTimeZone(),
            c.getDefaultInvoiceNote(), c.getDefaultQuotationNote(),
            c.getLogoObjectKey(), c.getStatus());
    }
}
