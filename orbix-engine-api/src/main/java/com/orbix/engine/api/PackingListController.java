package com.orbix.engine.api;

import com.orbix.engine.modules.sales.domain.dto.CreatePackingListRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PackingListDto;
import com.orbix.engine.modules.sales.service.PackingListService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Packing lists (F4.5). Gated by {@code SALES.MANAGE_PACKING}. */
@RestController
@RequestMapping("/api/v1/packing-lists")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('SALES.MANAGE_PACKING')")
public class PackingListController {

    private final PackingListService service;

    @GetMapping
    public List<PackingListDto> list(@RequestParam(required = false) Long branchId) {
        return service.list(branchId);
    }

    @GetMapping("/{id}")
    public PackingListDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<PackingListDto> create(
            @Valid @RequestBody CreatePackingListRequestDto request) {
        PackingListDto pl = service.createDraft(request);
        return ResponseEntity.created(URI.create("/api/v1/packing-lists/" + pl.id())).body(pl);
    }

    @PostMapping("/{id}/dispatch")
    public PackingListDto dispatch(@PathVariable Long id) {
        return service.dispatch(id);
    }

    @PostMapping("/{id}/deliver")
    public PackingListDto deliver(@PathVariable Long id) {
        return service.markDelivered(id);
    }

    @PostMapping("/{id}/cancel")
    public PackingListDto cancel(@PathVariable Long id) {
        return service.cancel(id);
    }
}
