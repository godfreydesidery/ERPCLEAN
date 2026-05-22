package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.CloseTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionDto;
import com.orbix.engine.modules.pos.service.TillSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Till sessions (F5.1). Open/close/reconcile gated by their own permissions. */
@RestController
@RequestMapping("/api/v1/till-sessions")
@RequiredArgsConstructor
public class TillSessionController {

    private final TillSessionService service;

    @GetMapping
    @PreAuthorize("hasAuthority('POS.MANAGE_TILL') or hasAuthority('POS.SESSION_OPEN') or hasAuthority('POS.SESSION_CLOSE')")
    public List<TillSessionDto> list(@RequestParam(required = false) Long branchId,
                                     @RequestParam(required = false) Long tillId) {
        if (tillId != null) {
            return service.listByTill(tillId);
        }
        return service.list(branchId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('POS.MANAGE_TILL') or hasAuthority('POS.SESSION_OPEN') or hasAuthority('POS.SESSION_CLOSE')")
    public TillSessionDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/open")
    @PreAuthorize("hasAuthority('POS.SESSION_OPEN')")
    public ResponseEntity<TillSessionDto> open(@Valid @RequestBody OpenTillSessionRequestDto request) {
        TillSessionDto session = service.open(request);
        return ResponseEntity.created(URI.create("/api/v1/till-sessions/" + session.id())).body(session);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('POS.SESSION_CLOSE')")
    public TillSessionDto close(@PathVariable Long id,
                                @Valid @RequestBody CloseTillSessionRequestDto request) {
        return service.close(id, request);
    }

    @PostMapping("/{id}/reconcile")
    @PreAuthorize("hasAuthority('POS.SESSION_RECONCILE')")
    public TillSessionDto reconcile(@PathVariable Long id) {
        return service.reconcile(id);
    }
}
