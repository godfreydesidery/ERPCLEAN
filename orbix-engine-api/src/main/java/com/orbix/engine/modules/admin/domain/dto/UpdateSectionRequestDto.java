package com.orbix.engine.modules.admin.domain.dto;

import com.orbix.engine.modules.admin.domain.enums.SectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload for editing a section. The section code is immutable. */
public record UpdateSectionRequestDto(
    @NotBlank @Size(max = 80) String name,
    @NotNull SectionType type,
    Long managerUserId
) {}
