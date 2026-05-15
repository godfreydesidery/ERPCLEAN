package com.orbix.engine.api;

import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;
import com.orbix.engine.modules.procurement.service.GrnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** GRN lifecycle (F3.2). Manage gated by {@code GRN.POST}; direct (no-LPO) GRNs also require {@code GRN.DIRECT}. */
@RestController
@RequestMapping("/api/v1/grns")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('GRN.POST')")
public class GrnController {

    private static final String DIRECT_PERMISSION = "GRN.DIRECT";

    private final GrnService service;

    @GetMapping
    public List<GrnDto> list(@RequestParam(required = false) Long branchId) {
        return service.list(branchId);
    }

    @GetMapping("/{id}")
    public GrnDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<GrnDto> create(@Valid @RequestBody CreateGrnRequestDto request) {
        if (request.lpoOrderId() == null && !callerHasAuthority(DIRECT_PERMISSION)) {
            throw new AccessDeniedException("Direct (no-LPO) GRN requires " + DIRECT_PERMISSION);
        }
        GrnDto grn = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/grns/" + grn.id())).body(grn);
    }

    @PostMapping("/{id}/post")
    public GrnDto post(@PathVariable Long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    public GrnDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    private boolean callerHasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority granted : auth.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
