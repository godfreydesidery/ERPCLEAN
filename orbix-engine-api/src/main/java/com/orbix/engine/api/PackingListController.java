package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.sales.domain.dto.CreatePackingListRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PackingListDto;
import com.orbix.engine.modules.sales.service.PackingListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Packing lists (F4.5). Gated by {@code SALES.MANAGE_PACKING}. */
@RestController
@RequestMapping("/api/v1/packing-lists")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('SALES.MANAGE_PACKING')")
public class PackingListController {

    private final PackingListService service;

    @GetMapping
    public List<PackingListDto> list(@RequestParam(required = false) Long branchId) {
        return service.list(branchId);
    }

    @GetMapping("/uid/{uid}")
    public PackingListDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<PackingListDto> create(
            @Valid @RequestBody CreatePackingListRequestDto request) {
        PackingListDto pl = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/packing-lists/uid/" + pl.uid())).body(pl);
    }

    @PostMapping("/uid/{uid}/dispatch")
    public PackingListDto dispatch(@PathVariable @ValidUlid String uid) {
        return service.dispatch(uid);
    }

    @PostMapping("/uid/{uid}/deliver")
    public PackingListDto deliver(@PathVariable @ValidUlid String uid) {
        return service.markDelivered(uid);
    }

    @PostMapping("/uid/{uid}/cancel")
    public PackingListDto cancel(@PathVariable @ValidUlid String uid) {
        return service.cancel(uid);
    }
}
