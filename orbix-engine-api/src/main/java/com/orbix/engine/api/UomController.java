package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;
import com.orbix.engine.modules.catalog.service.UomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Unit-of-measure registry (F1.4). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1/uoms")
@RequiredArgsConstructor
public class UomController {

    private final UomService service;

    @GetMapping
    public List<UomDto> listUoms() {
        return service.listUoms();
    }

    @GetMapping("/{id}")
    public UomDto getUom(@PathVariable Long id) {
        return service.getUom(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<UomDto> createUom(@Valid @RequestBody CreateUomRequestDto request) {
        UomDto uom = service.createUom(request);
        return ResponseEntity.created(URI.create("/api/v1/uoms/" + uom.id())).body(uom);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public UomDto updateUom(@PathVariable Long id, @Valid @RequestBody UpdateUomRequestDto request) {
        return service.updateUom(id, request);
    }
}
