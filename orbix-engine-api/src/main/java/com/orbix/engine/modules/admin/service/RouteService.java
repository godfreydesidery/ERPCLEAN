package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.dto.RouteDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateRouteRequestDto;

import java.util.List;

/** Delivery-route master management within the caller's company. */
public interface RouteService {

    List<RouteDto> listRoutes();

    RouteDto getRoute(Long routeId);

    RouteDto createRoute(CreateRouteRequestDto request);

    RouteDto updateRoute(Long routeId, UpdateRouteRequestDto request);

    /** Marks the route INACTIVE. Idempotency: rejects an already-inactive route. */
    void deactivateRoute(Long routeId);
}
