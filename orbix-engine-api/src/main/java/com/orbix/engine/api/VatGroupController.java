package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.VatGroupDto;
import com.orbix.engine.modules.catalog.service.VatGroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** VAT-group registry (F1.4). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1/vat-groups")
@RequiredArgsConstructor
public class VatGroupController {

    private final VatGroupService service;

    @GetMapping
    public List<VatGroupDto> listVatGroups() {
        return service.listVatGroups();
    }

    @GetMapping("/{id}")
    public VatGroupDto getVatGroup(@PathVariable Long id) {
        return service.getVatGroup(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<VatGroupDto> createVatGroup(
            @Valid @RequestBody CreateVatGroupRequestDto request) {
        VatGroupDto group = service.createVatGroup(request);
        return ResponseEntity.created(URI.create("/api/v1/vat-groups/" + group.id())).body(group);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public VatGroupDto updateVatGroup(@PathVariable Long id,
                                      @Valid @RequestBody UpdateVatGroupRequestDto request) {
        return service.updateVatGroup(id, request);
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archiveVatGroup(@PathVariable Long id) {
        service.archiveVatGroup(id);
        return ResponseEntity.noContent().build();
    }
}
