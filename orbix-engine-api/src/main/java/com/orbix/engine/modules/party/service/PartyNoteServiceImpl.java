package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.CreatePartyNoteRequestDto;
import com.orbix.engine.modules.party.domain.dto.PartyNoteDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.PartyNote;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;
import com.orbix.engine.modules.party.repository.PartyNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PartyNoteServiceImpl implements PartyNoteService {

    static final int DEFAULT_NOTE_LIMIT = 50;
    static final int MAX_NOTE_LIMIT = 200;

    private final PartyNoteRepository notes;
    private final PartyService partyService;
    private final RequestContext context;
    private final EventPublisher events;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "PartyNote")
    public PartyNoteDto addNote(CreatePartyNoteRequestDto request) {
        Party party = partyService.requireInCompanyByUid(request.customerUid());
        Long actorId = context.userId();
        PartyNote note = new PartyNote(
            party.getCompanyId(), party.getId(), request.kind(), request.body(), actorId);
        note = notes.save(note);

        Map<String, Object> payload = new HashMap<>();
        payload.put("noteId", note.getId());
        payload.put("partyId", note.getPartyId());
        payload.put("companyId", note.getCompanyId());
        payload.put("kind", note.getKind().name());
        payload.put("actorId", actorId);
        payload.put("createdAt", note.getCreatedAt());
        events.publish("PartyNoteCreated.v1", "PartyNote", String.valueOf(note.getId()), payload);
        return PartyNoteDto.from(note);
    }

    @Override
    @Transactional(readOnly = true)
    public PartyNoteDto getNoteByUid(String uid) {
        return PartyNoteDto.from(requireNoteByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "ARCHIVE", entityType = "PartyNote")
    public PartyNoteDto archiveNoteByUid(String uid) {
        PartyNote note = requireNoteByUid(uid);
        if (note.getStatus() == PartyNoteStatus.ARCHIVED) {
            throw new IllegalArgumentException("Note is already archived: " + uid);
        }
        Long actorId = context.userId();
        note.archive(actorId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("noteId", note.getId());
        payload.put("partyId", note.getPartyId());
        payload.put("companyId", note.getCompanyId());
        payload.put("actorId", actorId);
        payload.put("archivedAt", note.getArchivedAt());
        events.publish("PartyNoteArchived.v1", "PartyNote", String.valueOf(note.getId()), payload);
        return PartyNoteDto.from(note);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyNoteDto> listNotesForCustomerUid(String customerUid, boolean includeArchived, int limit) {
        Party party = partyService.requireInCompanyByUid(customerUid);
        int capped = clampLimit(limit);
        List<PartyNote> rows = includeArchived
            ? notes.findByPartyIdOrderByCreatedAtDescIdDesc(party.getId(), PageRequest.of(0, capped))
            : notes.findByPartyIdAndStatusOrderByCreatedAtDescIdDesc(
                party.getId(), PartyNoteStatus.ACTIVE, PageRequest.of(0, capped));
        return rows.stream().map(PartyNoteDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyNoteDto> listNotesForPartyUid(String partyUid, PartyNoteKind kind,
                                                    boolean includeArchived, int limit) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        int capped = clampLimit(limit);
        List<PartyNote> rows;
        if (kind != null) {
            PartyNoteStatus status = includeArchived ? null : PartyNoteStatus.ACTIVE;
            rows = (status != null)
                ? notes.findByPartyIdAndKindAndStatusOrderByCreatedAtDescIdDesc(
                    party.getId(), kind, status, PageRequest.of(0, capped))
                : notes.findByPartyIdAndKindOrderByCreatedAtDescIdDesc(
                    party.getId(), kind, PageRequest.of(0, capped));
        } else {
            rows = includeArchived
                ? notes.findByPartyIdOrderByCreatedAtDescIdDesc(party.getId(), PageRequest.of(0, capped))
                : notes.findByPartyIdAndStatusOrderByCreatedAtDescIdDesc(
                    party.getId(), PartyNoteStatus.ACTIVE, PageRequest.of(0, capped));
        }
        return rows.stream().map(PartyNoteDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PartyNoteDto> listRecentForPartyId(Long partyId,
                                                   PartyNoteKind kind,
                                                   PartyNoteStatus status,
                                                   int limit) {
        int capped = clampLimit(limit);
        Long companyId = context.companyId();
        List<PartyNote> rows = (kind != null)
            ? notes.findByCompanyIdAndKindAndStatusOrderByCreatedAtDescIdDesc(
                companyId, kind, status != null ? status : PartyNoteStatus.ACTIVE,
                PageRequest.of(0, capped))
            : (status != null
                ? notes.findByPartyIdAndStatusOrderByCreatedAtDescIdDesc(partyId, status, PageRequest.of(0, capped))
                : notes.findByPartyIdOrderByCreatedAtDescIdDesc(partyId, PageRequest.of(0, capped)));
        return rows.stream()
            .filter(n -> Objects.equals(n.getCompanyId(), companyId))
            .map(PartyNoteDto::from)
            .toList();
    }

    private PartyNote requireNoteByUid(String uid) {
        PartyNote note = notes.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Note not found: " + uid));
        if (!Objects.equals(note.getCompanyId(), context.companyId())) {
            // Cross-tenant access — same error as not-found, never leak existence.
            throw new NoSuchElementException("Note not found: " + uid);
        }
        return note;
    }

    private static int clampLimit(int requested) {
        if (requested <= 0) return DEFAULT_NOTE_LIMIT;
        return Math.min(requested, MAX_NOTE_LIMIT);
    }
}
