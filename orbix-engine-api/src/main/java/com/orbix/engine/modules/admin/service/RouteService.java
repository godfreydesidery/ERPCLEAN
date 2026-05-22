package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.dto.RouteDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateRouteRequestDto;

import java.util.List;

/** Delivery-route master management within the caller's company. */
public interface RouteService {

    List<RouteDto> listRoutes();

    RouteDto getRouteByUid(String uid);

    RouteDto createRoute(CreateRouteRequestDto request);

    RouteDto updateRouteByUid(String uid, UpdateRouteRequestDto request);

    /** Marks the route INACTIVE. Idempotency: rejects an already-inactive route. */
    void deactivateRouteByUid(String uid);
}
