package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.dto.RouteDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Route;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.RouteRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
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
class RouteServiceImplTest {

    private static final Long COMPANY_ID = 4L;
    private static final Long ACTOR_ID = 11L;
    private static final String UID = "0123456789ABCDEFGHJKMNPQRS";

    @Mock private RouteRepository routes;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private RouteServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static Route route(Long companyId, AdminStatus status) {
        Route r = new Route(companyId, "CENTRAL", "Central", "desc", ACTOR_ID);
        ReflectionTestUtils.setField(r, "uid", UID);
        r.setId(10L);
        r.setStatus(status);
        return r;
    }

    @Test
    void createRoute_persistsAndUppercasesCode() {
        when(routes.existsByCompanyIdAndCode(COMPANY_ID, "CENTRAL")).thenReturn(false);
        when(routes.save(any(Route.class))).thenAnswer(inv -> {
            Route r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "uid", UID);
            r.setId(10L);
            return r;
        });

        RouteDto dto = service.createRoute(new CreateRouteRequestDto("central", "Central", "desc"));

        assertThat(dto.code()).isEqualTo("CENTRAL");
        verify(events).publish(eq("RouteCreated.v1"), any(), any(), any());
    }

    @Test
    void createRoute_rejectsDuplicateCode() {
        when(routes.existsByCompanyIdAndCode(COMPANY_ID, "CENTRAL")).thenReturn(true);

        assertThatThrownBy(() -> service.createRoute(new CreateRouteRequestDto("CENTRAL", "Central", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(routes, never()).save(any());
    }

    @Test
    void updateRouteByUid_updatesDetails() {
        when(routes.findByUid(UID)).thenReturn(Optional.of(route(COMPANY_ID, AdminStatus.ACTIVE)));

        RouteDto dto = service.updateRouteByUid(UID, new UpdateRouteRequestDto("Central North", "new desc"));

        assertThat(dto.name()).isEqualTo("Central North");
        verify(events).publish(eq("RouteUpdated.v1"), any(), any(), any());
    }

    @Test
    void requireRoute_fromAnotherCompany_throwsNotFound() {
        when(routes.findByUid(UID)).thenReturn(Optional.of(route(999L, AdminStatus.ACTIVE)));

        assertThatThrownBy(() -> service.getRouteByUid(UID))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Route not found");
    }

    @Test
    void deactivateRouteByUid_marksInactiveAndEmitsReason() {
        Route active = route(COMPANY_ID, AdminStatus.ACTIVE);
        when(routes.findByUid(UID)).thenReturn(Optional.of(active));

        service.deactivateRouteByUid(UID, "seasonal close");

        assertThat(active.getStatus()).isEqualTo(AdminStatus.INACTIVE);
        verify(events).publish(eq("RouteDeactivated.v1"), any(), any(), any());
    }

    @Test
    void deactivateRouteByUid_rejectsAlreadyInactive() {
        when(routes.findByUid(UID)).thenReturn(Optional.of(route(COMPANY_ID, AdminStatus.INACTIVE)));

        assertThatThrownBy(() -> service.deactivateRouteByUid(UID, "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already inactive");
    }

    @Test
    void activateRouteByUid_marksActive() {
        Route inactive = route(COMPANY_ID, AdminStatus.INACTIVE);
        when(routes.findByUid(UID)).thenReturn(Optional.of(inactive));

        service.activateRouteByUid(UID, "reopened");

        assertThat(inactive.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        verify(events).publish(eq("RouteActivated.v1"), any(), any(), any());
    }

    @Test
    void activateRouteByUid_rejectsAlreadyActive() {
        when(routes.findByUid(UID)).thenReturn(Optional.of(route(COMPANY_ID, AdminStatus.ACTIVE)));

        assertThatThrownBy(() -> service.activateRouteByUid(UID, "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already active");
    }
}
