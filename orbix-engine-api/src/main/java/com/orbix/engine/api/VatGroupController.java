package com.orbix.engine.api;

import com.orbix.engine.modules.catalog.domain.dto.CreateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.VatGroupDto;
import com.orbix.engine.modules.catalog.service.VatGroupService;
import com.orbix.engine.modules.common.validation.ValidUlid;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** VAT-group registry (F1.4). Reuses the ITEM.* catalog permissions. */
@RestController
@RequestMapping("/api/v1/vat-groups")
@RequiredArgsConstructor
@Validated
public class VatGroupController {

    private final VatGroupService service;

    @GetMapping
    public List<VatGroupDto> listVatGroups() {
        return service.listVatGroups();
    }

    @GetMapping("/uid/{uid}")
    public VatGroupDto getVatGroup(@PathVariable @ValidUlid String uid) {
        return service.getVatGroupByUid(uid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ITEM.CREATE')")
    public ResponseEntity<VatGroupDto> createVatGroup(
            @Valid @RequestBody CreateVatGroupRequestDto request) {
        VatGroupDto group = service.createVatGroup(request);
        return ResponseEntity.created(URI.create("/api/v1/vat-groups/uid/" + group.uid())).body(group);
    }

    @PatchMapping("/uid/{uid}")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public VatGroupDto updateVatGroup(@PathVariable @ValidUlid String uid,
                                      @Valid @RequestBody UpdateVatGroupRequestDto request) {
        return service.updateVatGroupByUid(uid, request);
    }

    @PostMapping("/uid/{uid}/archive")
    @PreAuthorize("hasAuthority('ITEM.ARCHIVE')")
    public ResponseEntity<Void> archiveVatGroup(@PathVariable @ValidUlid String uid) {
        service.archiveVatGroupByUid(uid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{uid}/activate")
    @PreAuthorize("hasAuthority('ITEM.UPDATE')")
    public ResponseEntity<Void> activateVatGroup(@PathVariable @ValidUlid String uid) {
        service.activateVatGroupByUid(uid);
        return ResponseEntity.noContent().build();
    }
}
