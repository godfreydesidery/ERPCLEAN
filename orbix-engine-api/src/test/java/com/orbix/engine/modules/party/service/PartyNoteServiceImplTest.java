package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.party.domain.dto.CreatePartyNoteRequestDto;
import com.orbix.engine.modules.party.domain.dto.PartyNoteDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.PartyNote;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyNoteKind;
import com.orbix.engine.modules.party.domain.enums.PartyNoteStatus;
import com.orbix.engine.modules.party.repository.PartyNoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyNoteServiceImplTest {

    private static final Long COMPANY_ID = 9L;
    private static final Long ACTOR_ID = 11L;

    @Mock private PartyNoteRepository notes;
    @Mock private PartyService partyService;
    @Mock private RequestContext context;
    @Mock private EventPublisher events;

    @InjectMocks private PartyNoteServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private Party party(Long id, String name) {
        Party p = new Party(COMPANY_ID, "CUST-" + id, name, PartyCategory.INDIVIDUAL, ACTOR_ID);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "uid", UidGenerator.next());
        return p;
    }

    private PartyNote noteFor(Party party, PartyNoteKind kind) {
        PartyNote n = new PartyNote(party.getCompanyId(), party.getId(), kind, "Body", ACTOR_ID);
        ReflectionTestUtils.setField(n, "id", 42L);
        ReflectionTestUtils.setField(n, "uid", UidGenerator.next());
        return n;
    }

    @Test
    void addNote_persists_andEmitsOutboxEvent() {
        Party p = party(100L, "Acme");
        when(partyService.requireInCompanyByUid(p.getUid())).thenReturn(p);
        when(notes.save(any(PartyNote.class))).thenAnswer(inv -> {
            PartyNote n = inv.getArgument(0);
            ReflectionTestUtils.setField(n, "id", 42L);
            ReflectionTestUtils.setField(n, "uid", UidGenerator.next());
            return n;
        });

        PartyNoteDto dto = service.addNote(new CreatePartyNoteRequestDto(
            p.getUid(), PartyNoteKind.AR_CHASE, "Called - left voicemail"));

        assertThat(dto.partyId()).isEqualTo(p.getId());
        assertThat(dto.kind()).isEqualTo(PartyNoteKind.AR_CHASE);
        assertThat(dto.body()).isEqualTo("Called - left voicemail");
        assertThat(dto.status()).isEqualTo(PartyNoteStatus.ACTIVE);

        ArgumentCaptor<PartyNote> saved = ArgumentCaptor.forClass(PartyNote.class);
        verify(notes).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(ACTOR_ID);

        verify(events).publish(eq("PartyNoteCreated.v1"), eq("PartyNote"), any(), any());
    }

    @Test
    void addNote_unknownPartyUid_throws() {
        when(partyService.requireInCompanyByUid("unknown")).thenThrow(new NoSuchElementException("not found"));

        assertThatThrownBy(() -> service.addNote(new CreatePartyNoteRequestDto(
            "unknown", PartyNoteKind.AR_CHASE, "x")))
            .isInstanceOf(NoSuchElementException.class);
        verify(notes, never()).save(any());
    }

    @Test
    void archiveNote_setsStatusArchived_andEmitsEvent() {
        Party p = party(101L, "Bravo");
        PartyNote n = noteFor(p, PartyNoteKind.AR_CHASE);
        when(notes.findByUid(n.getUid())).thenReturn(Optional.of(n));

        PartyNoteDto dto = service.archiveNoteByUid(n.getUid());

        assertThat(dto.status()).isEqualTo(PartyNoteStatus.ARCHIVED);
        assertThat(n.getArchivedBy()).isEqualTo(ACTOR_ID);
        verify(events).publish(eq("PartyNoteArchived.v1"), eq("PartyNote"), any(), any());
    }

    @Test
    void archiveNote_doubleArchive_throws() {
        Party p = party(102L, "Charlie");
        PartyNote n = noteFor(p, PartyNoteKind.AR_CHASE);
        n.archive(ACTOR_ID);
        when(notes.findByUid(n.getUid())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.archiveNoteByUid(n.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already archived");
    }

    @Test
    void archiveNote_crossTenant_throwsNotFound() {
        Party p = party(103L, "Other Co");
        PartyNote n = noteFor(p, PartyNoteKind.AR_CHASE);
        ReflectionTestUtils.setField(n, "companyId", 9999L);
        when(notes.findByUid(n.getUid())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.archiveNoteByUid(n.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void listNotesForCustomerUid_capsLimit_andFiltersByStatus() {
        Party p = party(104L, "Delta");
        when(partyService.requireInCompanyByUid(p.getUid())).thenReturn(p);
        PartyNote a = noteFor(p, PartyNoteKind.AR_CHASE);
        when(notes.findByPartyIdAndStatusOrderByCreatedAtDescIdDesc(
            eq(p.getId()), eq(PartyNoteStatus.ACTIVE), any(Pageable.class)))
            .thenReturn(List.of(a));

        List<PartyNoteDto> active = service.listNotesForCustomerUid(p.getUid(), false, 50);

        assertThat(active).hasSize(1);
        assertThat(active.get(0).status()).isEqualTo(PartyNoteStatus.ACTIVE);
    }

    @Test
    void listNotesForCustomerUid_includeArchived_callsAllStatus() {
        Party p = party(105L, "Echo");
        when(partyService.requireInCompanyByUid(p.getUid())).thenReturn(p);
        when(notes.findByPartyIdOrderByCreatedAtDescIdDesc(eq(p.getId()), any(Pageable.class)))
            .thenReturn(List.of());

        List<PartyNoteDto> all = service.listNotesForCustomerUid(p.getUid(), true, 20);

        assertThat(all).isEmpty();
    }

    @Test
    void getNoteByUid_crossTenant_throwsNotFound() {
        Party p = party(106L, "Foxtrot");
        PartyNote n = noteFor(p, PartyNoteKind.AR_CHASE);
        ReflectionTestUtils.setField(n, "companyId", 8888L);
        when(notes.findByUid(n.getUid())).thenReturn(Optional.of(n));

        assertThatThrownBy(() -> service.getNoteByUid(n.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
