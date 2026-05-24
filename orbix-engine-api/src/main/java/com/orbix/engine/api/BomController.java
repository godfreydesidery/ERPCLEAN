package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.production.domain.dto.BomDto;
import com.orbix.engine.modules.production.domain.dto.CreateBomRequestDto;
import com.orbix.engine.modules.production.domain.dto.PatchBomRequestDto;
import com.orbix.engine.modules.production.domain.enums.BomStatus;
import com.orbix.engine.modules.production.service.BomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class BomController {

    private final BomService service;

    @PostMapping
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public ResponseEntity<BomDto> create(@Valid @RequestBody CreateBomRequestDto request) {
        BomDto created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/boms/uid/" + created.uid())).body(created);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PROD.READ_BOM') or hasAuthority('PROD.MANAGE_BOM')")
    public List<BomDto> list(@RequestParam(required = false) Long sectionId,
                             @RequestParam(required = false) Long outputItemId,
                             @RequestParam(required = false) BomStatus status) {
        return service.list(sectionId, outputItemId, status);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('PROD.READ_BOM') or hasAuthority('PROD.MANAGE_BOM')")
    public BomDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public BomDto patch(@PathVariable @ValidUlid String uid,
                        @Valid @RequestBody PatchBomRequestDto request) {
        return service.patch(uid, request);
    }

    @PostMapping("/uid/{uid}/activate")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public BomDto activate(@PathVariable @ValidUlid String uid) {
        return service.activate(uid);
    }

    @PostMapping("/uid/{uid}/retire")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public BomDto retire(@PathVariable @ValidUlid String uid) {
        return service.retire(uid);
    }

    @PostMapping("/uid/{uid}/version")
    @PreAuthorize("hasAuthority('PROD.MANAGE_BOM')")
    public ResponseEntity<BomDto> version(@PathVariable @ValidUlid String uid) {
        BomDto next = service.version(uid);
        return ResponseEntity.created(URI.create("/api/v1/boms/uid/" + next.uid())).body(next);
    }
}
