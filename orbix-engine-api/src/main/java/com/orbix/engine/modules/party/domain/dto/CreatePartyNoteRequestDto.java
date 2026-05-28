package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Slice G — request body for appending a chase note. The
 * {@code customerUid} addresses the party externally; the service maps
 * uid → party.id internally.
 */
public record CreatePartyNoteRequestDto(
    @ValidUlid @NotBlank String customerUid,
    @NotNull PartyNoteKind kind,
    @NotBlank @Size(max = 1000) String body
) {}
