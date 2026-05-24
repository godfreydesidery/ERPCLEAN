package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.dto.RouteDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Route;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.repository.RouteRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class RouteServiceImpl implements RouteService {

    private final RouteRepository routes;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<RouteDto> listRoutes() {
        return routes.findByCompanyId(context.companyId()).stream()
            .sorted(Comparator.comparing(Route::getCode))
            .map(RouteDto::from)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RouteDto getRouteByUid(String uid) {
        return RouteDto.from(requireRouteByUid(uid));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "Route")
    public RouteDto createRoute(CreateRouteRequestDto request) {
        Long companyId = context.companyId();
        String code = request.code().trim().toUpperCase();
        if (routes.existsByCompanyIdAndCode(companyId, code)) {
            throw new IllegalArgumentException("Route code already exists: " + code);
        }
        Route route = routes.save(new Route(
            companyId, code, request.name(), request.description(), context.userId()));
        events.publish("RouteCreated.v1", "Route", route.getUid(),
            Map.of(ROUTE_UID_KEY, route.getUid(), "companyId", companyId, "code", code));
        return RouteDto.from(route);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Route")
    public RouteDto updateRouteByUid(String uid, UpdateRouteRequestDto request) {
        Route route = requireRouteByUid(uid);
        route.updateDetails(request.name(), request.description(), context.userId());
        events.publish("RouteUpdated.v1", "Route", route.getUid(),
            Map.of(ROUTE_UID_KEY, route.getUid()));
        return RouteDto.from(route);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "Route")
    public void deactivateRouteByUid(String uid, String reason) {
        Route route = requireRouteByUid(uid);
        if (route.getStatus() != AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Route is already inactive: " + uid);
        }
        route.deactivate(context.userId());
        events.publish("RouteDeactivated.v1", "Route", route.getUid(),
            Map.of(ROUTE_UID_KEY, route.getUid(), "reason", reason));
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "Route")
    public void activateRouteByUid(String uid, String reason) {
        Route route = requireRouteByUid(uid);
        if (route.getStatus() == AdminStatus.ACTIVE) {
            throw new IllegalArgumentException("Route is already active: " + uid);
        }
        route.activate(context.userId());
        events.publish("RouteActivated.v1", "Route", route.getUid(),
            Map.of(ROUTE_UID_KEY, route.getUid(), "reason", reason));
    }

    private Route requireRouteByUid(String uid) {
        Route route = routes.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Route not found: " + uid));
        if (!route.getCompanyId().equals(context.companyId())) {
            throw new NoSuchElementException("Route not found: " + uid);
        }
        return route;
    }

    private static final String ROUTE_UID_KEY = "routeUid";
}
