package com.orbix.engine.modules.admin.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FirstRunRequestDto(
    @Valid @NotBlank OrganisationInfoDto organisation,
    @Valid CompanyInfoDto company,
    @Valid BranchInfoDto branch,
    @Valid AdminUserDto admin
) {

    public record OrganisationInfoDto(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 200) String legalName,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode
    ) {}

    public record CompanyInfoDto(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 64) String timeZone
    ) {}

    public record BranchInfoDto(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 64) String timeZone
    ) {}

    public record AdminUserDto(
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotBlank @Size(min = 8, max = 120) String password,
        @NotBlank @Size(max = 120) String displayName
    ) {}
}
