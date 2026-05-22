package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.CreateCustomerRequestDto;
import com.orbix.engine.modules.party.domain.dto.CustomerResponseDto;
import com.orbix.engine.modules.party.domain.dto.PartyDetailsDto;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CustomerServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long ACTOR_ID = 3L;

    @Mock private CustomerRepository customers;
    @Mock private PartyRepository parties;
    @Mock private PartyService partyService;
    @Mock private RequestContext context;

    @InjectMocks private CustomerServiceImpl service;

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

    private static CreateCustomerRequestDto createRequest() {
        PartyDetailsDto details = new PartyDetailsDto("Mama Sara", null, PartyCategory.BUSINESS,
            "999-1", null, null, null, null, null, null, null);
        return new CreateCustomerRequestDto("C-1", details, new BigDecimal("2000000"), 30,
            null, null, null, false);
    }

    @Test
    void createCustomer_attachesRoleToResolvedParty() {
        Party resolved = party(100L, "C-1");
        when(partyService.resolveOrCreate(eq("C-1"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(customers.existsById(100L)).thenReturn(false);
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponseDto result = service.createCustomer(createRequest());

        assertThat(result.partyId()).isEqualTo(100L);
        assertThat(result.creditTermsDays()).isEqualTo(30);
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertThat(saved.getValue().getPartyId()).isEqualTo(100L);
        assertThat(saved.getValue().getCreditLimitAmount()).isEqualByComparingTo("2000000");
    }

    @Test
    void createCustomer_whenPartyAlreadyHasCustomerRole_isRejected() {
        Party resolved = party(100L, "C-1");
        when(partyService.resolveOrCreate(eq("C-1"), any(), eq(ACTOR_ID))).thenReturn(resolved);
        when(customers.existsById(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.createCustomer(createRequest()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already has a customer role");
        verify(customers, never()).save(any());
    }

    @Test
    void deactivateCustomer_delegatesToPartyService() {
        when(customers.findById(100L)).thenReturn(java.util.Optional.of(new Customer(100L)));

        service.deactivateCustomer(100L);

        verify(partyService).deactivate(100L);
    }

    @Test
    void deactivateCustomer_whenNotACustomer_throwsNotFound() {
        when(customers.findById(100L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.deactivateCustomer(100L))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void createWalkInCustomer_createsPartyAndWalkInRow() {
        when(parties.existsByCompanyIdAndCode(COMPANY_ID, "WALKIN-12")).thenReturn(false);
        when(parties.save(any(Party.class))).thenAnswer(inv -> {
            Party p = inv.getArgument(0);
            p.setId(200L);
            return p;
        });

        service.createWalkInCustomer(12L);

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(customers).save(saved.capture());
        assertThat(saved.getValue().isWalkIn()).isTrue();
        assertThat(saved.getValue().getDefaultBranchId()).isEqualTo(12L);
    }

    @Test
    void createWalkInCustomer_isIdempotentWhenAlreadyPresent() {
        when(parties.existsByCompanyIdAndCode(COMPANY_ID, "WALKIN-12")).thenReturn(true);

        service.createWalkInCustomer(12L);

        verify(parties, never()).save(any());
        verify(customers, never()).save(any());
    }
}
