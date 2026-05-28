package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.entity.PartyNote;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;

import java.time.Instant;

/**
 * Wire shape for {@link PartyNote}. Long-id fields stringify globally via
 * {@code IdLongAsStringSerializerModifier}.
 */
public record PartyNoteDto(
    Long id,
    String uid,
    Long partyId,
    PartyNoteKind kind,
    String body,
    PartyNoteStatus status,
    Instant createdAt,
    Long createdBy,
    Instant archivedAt,
    Long archivedBy
) {
    public static PartyNoteDto from(PartyNote note) {
        return new PartyNoteDto(
            note.getId(),
            note.getUid(),
            note.getPartyId(),
            note.getKind(),
            note.getBody(),
            note.getStatus(),
            note.getCreatedAt(),
            note.getCreatedBy(),
            note.getArchivedAt(),
            note.getArchivedBy()
        );
    }
}
