package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;
import com.orbix.engine.modules.catalog.service.UomService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Unit-of-measure registry (F1.4). UoM is global, so it has its own UOM.MANAGE permission. */
@RestController
@RequestMapping("/api/v1/uoms")
@RequiredArgsConstructor
@Validated
public class UomController {

    private final UomService service;

    @GetMapping
    public List<UomDto> listUoms() {
        return service.listUoms();
    }

    @GetMapping("/uid/{uid}")
    public UomDto getUom(@PathVariable @ValidUlid String uid) {
        return service.getUomByUid(uid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('UOM.MANAGE')")
    public ResponseEntity<UomDto> createUom(@Valid @RequestBody CreateUomRequestDto request) {
        UomDto uom = service.createUom(request);
        return ResponseEntity.created(URI.create("/api/v1/uoms/uid/" + uom.uid())).body(uom);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('UOM.MANAGE')")
    public UomDto updateUom(@PathVariable @ValidUlid String uid,
                            @Valid @RequestBody UpdateUomRequestDto request) {
        return service.updateUomByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('UOM.MANAGE')")
    public ResponseEntity<Void> archiveUom(@PathVariable @ValidUlid String uid) {
        service.archiveUomByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{uid}/activate")
    @PreAuthorize("hasAuthority('UOM.MANAGE')")
    public ResponseEntity<Void> activateUom(@PathVariable @ValidUlid String uid) {
        service.activateUomByUid(uid);
        return ResponseEntity.noContent().build();
    }
}
