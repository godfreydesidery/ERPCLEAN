package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.admin.domain.entity.Route;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.RouteRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.UpdateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.SalesAgent;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.party.repository.SalesAgentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Focused on the route-assignment validation added to sales agents. */
@ExtendWith(MockitoExtension.class)
class SalesAgentServiceImplTest {

    private static final Long COMPANY_ID = 9L;
    private static final Long PARTY_ID = 50L;
    private static final String PARTY_UID = "0123456789ABCDEFGHJKMNPQRS";
    private static final Long ROUTE_ID = 7L;

    @Mock private SalesAgentRepository salesAgents;
    @Mock private PartyRepository parties;
    @Mock private PartyService partyService;
    @Mock private RouteRepository routes;
    @Mock private RequestContext context;
    @Mock private Party party;

    @InjectMocks private SalesAgentServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(partyService.requireInCompanyByUid(PARTY_UID)).thenReturn(party);
        lenient().when(party.getId()).thenReturn(PARTY_ID);
        lenient().when(salesAgents.findById(PARTY_ID))
            .thenReturn(Optional.of(new SalesAgent(PARTY_ID, "AGT1", 1L)));
    }

    private static UpdateSalesAgentRequestDto req(Long routeId) {
        return new UpdateSalesAgentRequestDto(null, null, routeId, null, 1L);
    }

    private static Route route(Long companyId, AdminStatus status) {
        Route r = new Route(companyId, "CENTRAL", "Central", null, 1L);
        r.setStatus(status);
        return r;
    }

    @Test
    void update_rejectsUnknownRoute() {
        when(routes.findById(ROUTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSalesAgentByPartyUid(PARTY_UID, req(ROUTE_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Route not found");
    }

    @Test
    void update_rejectsRouteFromAnotherCompany() {
        when(routes.findById(ROUTE_ID)).thenReturn(Optional.of(route(999L, AdminStatus.ACTIVE)));
        when(context.companyId()).thenReturn(COMPANY_ID);

        assertThatThrownBy(() -> service.updateSalesAgentByPartyUid(PARTY_UID, req(ROUTE_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Route not found");
    }

    @Test
    void update_rejectsInactiveRoute() {
        when(routes.findById(ROUTE_ID)).thenReturn(Optional.of(route(COMPANY_ID, AdminStatus.INACTIVE)));
        when(context.companyId()).thenReturn(COMPANY_ID);

        assertThatThrownBy(() -> service.updateSalesAgentByPartyUid(PARTY_UID, req(ROUTE_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not active");
    }

    @Test
    void archiveSalesAgent_delegatesToPartyService() {
        service.archiveSalesAgentByPartyUid(PARTY_UID);

        verify(partyService).archive(PARTY_ID);
    }

    @Test
    void activateSalesAgent_delegatesToPartyService() {
        service.activateSalesAgentByPartyUid(PARTY_UID);

        verify(partyService).activate(PARTY_ID);
    }
}
