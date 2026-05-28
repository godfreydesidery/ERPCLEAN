package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreatePartyNoteRequestDto;
import com.orbix.engine.modules.party.domain.dto.PartyNoteDto;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;

import java.util.List;

/**
 * Slice G — chase-note CRUD on a {@link com.orbix.engine.modules.party.domain.entity.Party}.
 * Generalised across AR + AP so Slice G.1 (supplier-AP dunning) reuses
 * the same surface.
 */
public interface PartyNoteService {

    /** Append a chase note. The party is addressed externally by uid. */
    PartyNoteDto addNote(CreatePartyNoteRequestDto request);

    /** Get a single note by uid. Tenant-scoped to the caller's company. */
    PartyNoteDto getNoteByUid(String uid);

    /** Archive a note (lifecycle ACTIVE -> ARCHIVED). Re-archiving throws. */
    PartyNoteDto archiveNoteByUid(String uid);

    /**
     * Activity-log for a customer drill-down — newest first. {@code limit}
     * caps the page size (clamped server-side). {@code includeArchived}
     * controls whether ARCHIVED rows are returned.
     */
    List<PartyNoteDto> listNotesForCustomerUid(String customerUid, boolean includeArchived, int limit);

    /** Same as {@link #listNotesForCustomerUid} but by party id (internal joins). */
    List<PartyNoteDto> listRecentForPartyId(Long partyId, PartyNoteKind kind, PartyNoteStatus status, int limit);
}
