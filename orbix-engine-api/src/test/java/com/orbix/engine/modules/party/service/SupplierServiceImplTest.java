package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.PartyDetailsDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.party.repository.SupplierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long ACTOR_ID = 3L;

    @Mock private SupplierRepository suppliers;
    @Mock private PartyRepository parties;
    @Mock private PartyService partyService;
    @Mock private RequestContext context;

    @InjectMocks private SupplierServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        // Backend auto-allocates the party code for the create-new path now.
        lenient().when(partyService.reservePartyCode("SUP")).thenReturn("SUP0001");
    }

    private static Party party(Long id, String code) {
        Party party = new Party(COMPANY_ID, code, "Name " + code, PartyCategory.BUSINESS, ACTOR_ID);
        party.setId(id);
        ReflectionTestUtils.setField(party, "uid", UidGenerator.next());
        return party;
    }

    private static CreateSupplierRequestDto createRequest() {
        PartyDetailsDto details = new PartyDetailsDto("Acme Distributors", null, PartyCategory.BUSINESS,
            "999-1", null, null, null, null, null, null, null);
        return new CreateSupplierRequestDto(null, details, 30, BigDecimal.ZERO,
            "UGX", "Stanbic", "0123456789", 7);
    }

    @Test
    void createSupplier_attachesRoleToResolvedParty() {
        Party resolved = party(100L, "SUP0001");
        when(partyService.resolveOrCreate(eq("SUP0001"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(suppliers.existsById(100L)).thenReturn(false);
        when(suppliers.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierResponseDto result = service.createSupplier(createRequest());

        assertThat(result.partyId()).isEqualTo(100L);
        assertThat(result.paymentTermsDays()).isEqualTo(30);
        assertThat(result.bankName()).isEqualTo("Stanbic");
    }

    @Test
    void createSupplier_reusesPartyThatAlreadyHasAnotherRole() {
        // The shared-party rule: partyService resolved an existing party (e.g. already a customer).
        Party existingParty = party(540L, "CUST0540");
        when(partyService.resolveOrCreate(eq("SUP0001"), any(), eq(ACTOR_ID))).thenReturn(existingParty);
        when(suppliers.existsById(540L)).thenReturn(false);
        when(suppliers.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierResponseDto result = service.createSupplier(createRequest());

        assertThat(result.partyId()).isEqualTo(540L);
        assertThat(result.party().code()).isEqualTo("CUST0540");
    }

    @Test
    void createSupplier_whenPartyAlreadyHasSupplierRole_isRejected() {
        Party resolved = party(100L, "SUP0001");
        when(partyService.resolveOrCreate(eq("SUP0001"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(suppliers.existsById(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.createSupplier(createRequest()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has a supplier role");
        verify(suppliers, never()).save(any());
    }

    @Test
    void archiveSupplier_delegatesToPartyService() {
        Party party = party(100L, "SUP0001");
        when(partyService.requireInCompanyByUid(party.getUid())).thenReturn(party);
        when(suppliers.findById(100L)).thenReturn(java.util.Optional.of(new Supplier(100L)));

        service.archiveSupplierByPartyUid(party.getUid());

        verify(partyService).archive(100L);
    }

    @Test
    void archiveSupplier_whenNotASupplier_throwsNotFound() {
        Party party = party(100L, "SUP0001");
        when(partyService.requireInCompanyByUid(party.getUid())).thenReturn(party);
        when(suppliers.findById(100L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.archiveSupplierByPartyUid(party.getUid()))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void activateSupplier_delegatesToPartyService() {
        Party party = party(100L, "SUP0001");
        when(partyService.requireInCompanyByUid(party.getUid())).thenReturn(party);
        when(suppliers.findById(100L)).thenReturn(java.util.Optional.of(new Supplier(100L)));

        service.activateSupplierByPartyUid(party.getUid());

        verify(partyService).activate(100L);
    }
}
