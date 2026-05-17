package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.CreateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.dto.RouteDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateRouteRequestDto;
import com.orbix.engine.modules.admin.service.RouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Delivery-route management. Gated by {@code ADMIN.MANAGE_ROUTES}. */
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_ROUTES')")
public class RouteController {

    private final RouteService service;

    @GetMapping
    public List<RouteDto> listRoutes() {
        return service.listRoutes();
    }

    @GetMapping("/{id}")
    public RouteDto getRoute(@PathVariable Long id) {
        return service.getRoute(id);
    }

    @PostMapping
    public ResponseEntity<RouteDto> createRoute(
            @Valid @RequestBody CreateRouteRequestDto request) {
        RouteDto route = service.createRoute(request);
        return ResponseEntity.created(URI.create("/api/v1/routes/" + route.id())).body(route);
    }

    @PatchMapping("/{id}")
    public RouteDto updateRoute(@PathVariable Long id,
                                @Valid @RequestBody UpdateRouteRequestDto request) {
        return service.updateRoute(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateRoute(@PathVariable Long id) {
        service.deactivateRoute(id);
        return ResponseEntity.noContent().build();
    }
}
