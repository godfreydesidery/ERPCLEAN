package com.orbix.engine.api;

import com.orbix.engine.modules.production.domain.dto.BomDto;
import com.orbix.engine.modules.production.domain.dto.CreateBomRequestDto;
import com.orbix.engine.modules.production.domain.dto.PatchBomRequestDto;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import com.orbix.engine.modules.production.service.BomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * BOM authoring (F7.3a). Two permission gates — {@code PROD.MANAGE_BOM} for
 * production / kitchen managers who author the recipe; {@code PROD.READ_BOM}
 * for floor staff who only inspect.
 */
@RestController
@RequestMapping("/api/v1/boms")
@RequiredArgsConstructor
public class BomController {

    private final BomService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public ResponseEntity<BomDto> create(@Valid @RequestBody CreateBomRequestDto request) {
        BomDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/boms/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.READ_BOM') or hasAuthority('PROD.MANAGE_BOM')")
    public List<BomDto> list(@RequestParam(required = false) Long sectionId,
                             @RequestParam(required = false) Long outputItemId,
                             @RequestParam(required = false) BomStatus status) {
        return service.list(sectionId, outputItemId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PROD.READ_BOM') or hasAuthority('PROD.MANAGE_BOM')")
    public BomDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public BomDto patch(@PathVariable Long id,
                        @Valid @RequestBody PatchBomRequestDto request) {
        return service.patch(id, request);
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public BomDto activate(@PathVariable Long id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public BomDto retire(@PathVariable Long id) {
        return service.retire(id);
    }

    @PostMapping("/{id}/version")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public ResponseEntity<BomDto> version(@PathVariable Long id) {
        BomDto next = service.version(id);
        return ResponseEntity.created(URI.create("/api/v1/boms/" + next.id())).body(next);
    }
}
