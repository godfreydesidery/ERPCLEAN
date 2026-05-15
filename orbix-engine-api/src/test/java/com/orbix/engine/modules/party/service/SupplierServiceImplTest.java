package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.RequestContext;
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
    }

    private static Party party(Long id, String code) {
        Party party = new Party(COMPANY_ID, code, "Name " + code, PartyCategory.BUSINESS, ACTOR_ID);
        party.setId(id);
        return party;
    }

    private static CreateSupplierRequestDto createRequest() {
        PartyDetailsDto details = new PartyDetailsDto("Acme Distributors", null, PartyCategory.BUSINESS,
            "999-1", null, null, null, null, null, null, null);
        return new CreateSupplierRequestDto("S-1", details, 30, BigDecimal.ZERO,
            "UGX", "Stanbic", "0123456789", 7);
    }

    @Test
    void createSupplier_attachesRoleToResolvedParty() {
        Party resolved = party(100L, "S-1");
        when(partyService.resolveOrCreate(eq("S-1"), any(), eq(ACTOR_ID))).thenReturn(resolved);
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
        Party existingParty = party(540L, "C-540");
        when(partyService.resolveOrCreate(eq("S-1"), any(), eq(ACTOR_ID))).thenReturn(existingParty);
        when(suppliers.existsById(540L)).thenReturn(false);
        when(suppliers.save(any(Supplier.class))).thenAnswer(inv -> inv.getArgument(0));

        SupplierResponseDto result = service.createSupplier(createRequest());

        assertThat(result.partyId()).isEqualTo(540L);
        assertThat(result.party().code()).isEqualTo("C-540");
    }

    @Test
    void createSupplier_whenPartyAlreadyHasSupplierRole_isRejected() {
        Party resolved = party(100L, "S-1");
        when(partyService.resolveOrCreate(eq("S-1"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(suppliers.existsById(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.createSupplier(createRequest()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has a supplier role");
        verify(suppliers, never()).save(any());
    }

    @Test
    void deactivateSupplier_delegatesToPartyService() {
        when(suppliers.findById(100L)).thenReturn(java.util.Optional.of(new Supplier(100L)));

        service.deactivateSupplier(100L);

        verify(partyService).deactivate(100L);
    }
}
