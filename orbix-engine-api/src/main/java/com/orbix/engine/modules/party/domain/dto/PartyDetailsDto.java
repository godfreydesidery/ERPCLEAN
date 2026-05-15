package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The editable party-level fields, shared by every role's create / update payload. */
public record PartyDetailsDto(
    @NotBlank @Size(max = 200) String name,
    @Size(max = 200) String legalName,
    @NotNull PartyCategory category,
    @Size(max = 40) String tin,
    @Size(max = 40) String vrn,
    @Size(max = 40) String phone,
    @Email @Size(max = 120) String email,
    @Size(max = 4000) String physicalAddress,
    @Size(max = 4000) String postalAddress,
    @Size(max = 2) String countryCode,
    @Size(max = 4000) String notes
) {}
