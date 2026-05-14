package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;

/** The party-level view embedded in every role response. */
public record PartyResponseDto(
    Long id,
    Long companyId,
    String code,
    String name,
    String legalName,
    PartyCategory category,
    String tin,
    String vrn,
    String phone,
    String email,
    String physicalAddress,
    String postalAddress,
    String countryCode,
    String notes,
    PartyStatus status
) {
    public static PartyResponseDto from(Party party) {
        return new PartyResponseDto(
            party.getId(),
            party.getCompanyId(),
            party.getCode(),
            party.getName(),
            party.getLegalName(),
            party.getCategory(),
            party.getTin(),
            party.getVrn(),
            party.getPhone(),
            party.getEmail(),
            party.getPhysicalAddress(),
            party.getPostalAddress(),
            party.getCountryCode(),
            party.getNotes(),
            party.getStatus()
        );
    }
}
