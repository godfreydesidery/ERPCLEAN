package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.PartyDetailsDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.repository.PartyCodeSequenceRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class PartyServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long ACTOR_ID = 3L;

    @Mock private PartyRepository parties;
    @Mock private PartyCodeSequenceRepository codeSequences;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private PartyServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static Party party(Long id, Long companyId, String code, String tin) {
        Party party = new Party(companyId, code, "Name " + code, PartyCategory.BUSINESS, ACTOR_ID);
        party.setId(id);
        party.setTin(tin);
        return party;
    }

    private static PartyDetailsDto details(String name, String tin) {
        return new PartyDetailsDto(name, null, PartyCategory.BUSINESS, tin, null, null, null,
            null, null, null, null);
    }

    @Test
    void resolveOrCreate_withNewTin_createsParty() {
        when(parties.findFirstByCompanyIdAndTinAndTinNotNull(COMPANY_ID, "999-1")).thenReturn(Optional.empty());
        when(parties.existsByCompanyIdAndCode(COMPANY_ID, "C-1")).thenReturn(false);
        when(parties.save(any(Party.class))).thenAnswer(inv -> {
            Party p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        Party result = service.resolveOrCreate("c-1", details("Mama Sara", "999-1"), ACTOR_ID);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getCode()).isEqualTo("C-1");
        assertThat(result.getTin()).isEqualTo("999-1");
    }

    @Test
    void resolveOrCreate_withExistingTin_reusesPartyAndDoesNotSave() {
        Party existing = party(50L, COMPANY_ID, "C-EXISTING", "999-1");
        when(parties.findFirstByCompanyIdAndTinAndTinNotNull(COMPANY_ID, "999-1"))
            .thenReturn(Optional.of(existing));

        Party result = service.resolveOrCreate("C-NEW", details("Mama Sara", "999-1"), ACTOR_ID);

        assertThat(result).isSameAs(existing);
        verify(parties, never()).save(any());
    }

    @Test
    void resolveOrCreate_duplicateCodeWithNoTinMatch_isRejected() {
        when(parties.existsByCompanyIdAndCode(COMPANY_ID, "C-1")).thenReturn(true);

        assertThatThrownBy(() -> service.resolveOrCreate("C-1", details("X", null), ACTOR_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(parties, never()).save(any());
    }

    @Test
    void findByTin_returnsMatchingParty() {
        when(parties.findFirstByCompanyIdAndTinAndTinNotNull(COMPANY_ID, "999-1"))
            .thenReturn(Optional.of(party(50L, COMPANY_ID, "C-1", "999-1")));

        assertThat(service.findByTin("999-1")).isPresent();
        assertThat(service.findByTin("  ")).isEmpty();
    }

    @Test
    void requireInCompany_fromAnotherCompany_throwsNotFound() {
        when(parties.findById(9L)).thenReturn(Optional.of(party(9L, 999L, "C-9", null)));

        assertThatThrownBy(() -> service.requireInCompany(9L))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deactivate_setsInactiveAndEmitsEvent() {
        Party existing = party(50L, COMPANY_ID, "C-1", null);
        ReflectionTestUtils.setField(existing, "uid", "01ARZ3NDEKTSV4RRFFQ69G5FAV");
        when(parties.findById(50L)).thenReturn(Optional.of(existing));

        service.deactivate(50L);

        assertThat(existing.getStatus()).isEqualTo(PartyStatus.INACTIVE);
        verify(events).publish(eq("PartyDeactivated.v1"), any(), any(), any());
    }
}
