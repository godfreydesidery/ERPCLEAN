package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.procurement.domain.dto.CreateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.LpoOrderDto;
import com.orbix.engine.modules.procurement.domain.dto.UpdateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.service.LpoOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/** LPO lifecycle (F3.1). Manage-paths gated by {@code PROCUREMENT.MANAGE_LPO}; approval by {@code PROCUREMENT.APPROVE_LPO}. */
@RestController
@RequestMapping("/api/v1/lpos")
@RequiredArgsConstructor
@Validated
public class LpoOrderController {

    private final LpoOrderService service;

    @GetMapping
    @PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
    public PageDto<LpoOrderDto> list(@RequestParam(required = false) Long branchId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        return service.list(branchId, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
    public LpoOrderDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
    public ResponseEntity<LpoOrderDto> create(@Valid @RequestBody CreateLpoOrderRequestDto request) {
        LpoOrderDto order = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/lpos/uid/" + order.uid())).body(order);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
    public LpoOrderDto update(@PathVariable @ValidUlid String uid, @Valid @RequestBody UpdateLpoOrderRequestDto request) {
        return service.updateDraft(uid, request);
    }

    @PostMapping("/uid/{uid}/submit")
    @PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
    public LpoOrderDto submit(@PathVariable @ValidUlid String uid) {
        return service.submit(uid);
    }

    @PostMapping("/uid/{uid}/approve")
    @PreAuthorize("hasAuthority('PROCUREMENT.APPROVE_LPO')")
    public LpoOrderDto approve(@PathVariable @ValidUlid String uid) {
        return service.approve(uid);
    }

    @PostMapping("/uid/{uid}/cancel")
    @PreAuthorize("hasAuthority('PROCUREMENT.MANAGE_LPO')")
    public LpoOrderDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }
}
