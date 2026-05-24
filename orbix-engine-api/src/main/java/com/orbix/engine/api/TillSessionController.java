package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.pos.domain.dto.CloseTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionDto;
import com.orbix.engine.modules.pos.service.TillSessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Till sessions (F5.1). Open/close/reconcile gated by their own permissions. */
@RestController
@RequestMapping("/api/v1/till-sessions")
@RequiredArgsConstructor
@Validated
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

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('POS.MANAGE_TILL') or hasAuthority('POS.SESSION_OPEN') or hasAuthority('POS.SESSION_CLOSE')")
    public TillSessionDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping("/open")
    @PreAuthorize("hasAuthority('POS.SESSION_OPEN')")
    public ResponseEntity<TillSessionDto> open(@Valid @RequestBody OpenTillSessionRequestDto request) {
        TillSessionDto session = service.open(request);
        return ResponseEntity.created(URI.create("/api/v1/till-sessions/uid/" + session.uid())).body(session);
    }

    @PostMapping("/uid/{uid}/close")
    @PreAuthorize("hasAuthority('POS.SESSION_CLOSE')")
    public TillSessionDto close(@PathVariable @ValidUlid String uid,
                                @Valid @RequestBody CloseTillSessionRequestDto request) {
        return service.close(uid, request);
    }

    @PostMapping("/uid/{uid}/reconcile")
    @PreAuthorize("hasAuthority('POS.SESSION_RECONCILE')")
    public TillSessionDto reconcile(@PathVariable @ValidUlid String uid) {
        return service.reconcile(uid);
    }
}
