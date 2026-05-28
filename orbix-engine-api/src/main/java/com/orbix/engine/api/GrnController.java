package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.procurement.domain.dto.CancelPostedGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.CancelReasonRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import com.orbix.engine.modules.procurement.service.GrnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** GRN lifecycle (F3.2). Manage gated by {@code GRN.POST}; direct (no-LPO) GRNs also require {@code GRN.DIRECT}. */
@RestController
@RequestMapping("/api/v1/grns")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('GRN.POST')")
public class GrnController {

    private static final String DIRECT_PERMISSION = "GRN.DIRECT";

    private final GrnService service;

    @GetMapping
    public PageDto<GrnDto> list(@RequestParam(required = false) Long branchId,
                               @RequestParam(required = false) Long supplierId,
                               @RequestParam(required = false) GrnStatus status,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, supplierId, status, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    public GrnDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<GrnDto> create(@Valid @RequestBody CreateGrnRequestDto request) {
        if (request.lpoOrderId() == null && !callerHasAuthority(DIRECT_PERMISSION)) {
            throw new AccessDeniedException("Direct (no-LPO) GRN requires " + DIRECT_PERMISSION);
        }
        GrnDto grn = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/grns/uid/" + grn.uid())).body(grn);
    }

    @PostMapping("/uid/{uid}/post")
    public GrnDto post(@PathVariable @ValidUlid String uid) {
        return service.post(uid);
    }

    /** DRAFT → CANCELLED. Body is optional; if present, reason is persisted. */
    @PostMapping("/uid/{uid}/cancel")
    public GrnDto cancel(@PathVariable @ValidUlid String uid,
                         @Valid @RequestBody(required = false) CancelReasonRequestDto body) {
        String reason = body != null ? body.reason() : null;
        return service.cancel(uid, reason);
    }

    /**
     * POSTED → CANCELLED with compensating stock + outbox event. Requires the
     * dedicated {@code GRN.CANCEL} permission (overrides the class-level
     * {@code GRN.POST} gate via {@code hasAnyAuthority}; either permission is
     * accepted but the cancel-posted action is meant for the supervisor band).
     * Reason is mandatory.
     */
    @PostMapping("/uid/{uid}/cancel-posted")
    @PreAuthorize("hasAuthority('GRN.CANCEL')")
    public GrnDto cancelPosted(@PathVariable @ValidUlid String uid,
                               @Valid @RequestBody CancelPostedGrnRequestDto body) {
        return service.cancelPosted(uid, body.reason());
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
