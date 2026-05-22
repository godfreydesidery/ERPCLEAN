package com.orbix.engine.api;

import com.orbix.engine.modules.admin.domain.dto.CreateRouteRequestDto;
import com.orbix.engine.modules.admin.domain.dto.RouteDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateRouteRequestDto;
import com.orbix.engine.modules.admin.service.RouteService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Delivery-route management. Gated by {@code ADMIN.MANAGE_ROUTES}. */
@RestController
@RequestMapping("/api/v1/routes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ADMIN.MANAGE_ROUTES')")
@Validated
public class RouteController {

    private final RouteService service;

    @GetMapping
    public List<RouteDto> listRoutes() {
        return service.listRoutes();
    }

    @GetMapping("/uid/{uid}")
    public RouteDto getRoute(@PathVariable @ValidUlid String uid) {
        return service.getRouteByUid(uid);
    }

    @PostMapping
    public ResponseEntity<RouteDto> createRoute(
            @Valid @RequestBody CreateRouteRequestDto request) {
        RouteDto route = service.createRoute(request);
        return ResponseEntity.created(URI.create("/api/v1/routes/uid/" + route.uid())).body(route);
    }

    @PatchMapping("/uid/{uid}")
    public RouteDto updateRoute(@PathVariable @ValidUlid String uid,
                                @Valid @RequestBody UpdateRouteRequestDto request) {
        return service.updateRouteByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/deactivate")
    public ResponseEntity<Void> deactivateRoute(@PathVariable @ValidUlid String uid) {
        service.deactivateRouteByUid(uid);
        return ResponseEntity.noContent().build();
    }
}
