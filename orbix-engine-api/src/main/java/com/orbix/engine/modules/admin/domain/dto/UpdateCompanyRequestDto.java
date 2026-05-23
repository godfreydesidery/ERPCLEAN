package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable company-profile fields (US-COMP-002). The immutable company code is not editable. */
public record UpdateCompanyRequestDto(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) String legalName,
    @Size(max = 40) String tin,
    @Size(max = 40) String vrn,
    String physicalAddress,
    String postalAddress,
    @Size(max = 40) String phone,
    @Size(max = 120) String email,
    @Size(max = 200) String website,
    @NotBlank @Size(min = 3, max = 3) String currencyCode,
    @NotBlank @Size(min = 2, max = 2) String countryCode,
    @NotBlank @Size(max = 64) String timeZone,
    String defaultInvoiceNote,
    String defaultQuotationNote
) {}
