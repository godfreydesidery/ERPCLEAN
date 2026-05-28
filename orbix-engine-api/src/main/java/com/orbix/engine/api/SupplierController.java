package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Supplier management (F1.7). Per-action permissions:
 * {@code SUPPLIER.CREATE}, {@code SUPPLIER.UPDATE}, {@code SUPPLIER.ARCHIVE}
 * (archive perm also gates re-activation, the inverse of archive).
 */
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@Validated
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    public PageDto<SupplierResponseDto> listSuppliers(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PartyStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listSuppliers(q, status, PageRequest.of(page, size));
    }

    @GetMapping("/uid/{partyUid}")
    public SupplierResponseDto getSupplier(@PathVariable @ValidUlid String partyUid) {
        return service.getSupplierByPartyUid(partyUid);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUPPLIER.CREATE')")
    public ResponseEntity<SupplierResponseDto> createSupplier(
            @Valid @RequestBody CreateSupplierRequestDto request) {
        SupplierResponseDto supplier = service.createSupplier(request);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/uid/" + supplier.party().uid()))
            .body(supplier);
    }

    @PatchMapping("/uid/{partyUid}")
    @PreAuthorize("hasAuthority('SUPPLIER.UPDATE')")
    public SupplierResponseDto updateSupplier(@PathVariable @ValidUlid String partyUid,
                                              @Valid @RequestBody UpdateSupplierRequestDto request) {
        return service.updateSupplierByPartyUid(partyUid, request);
    }

    @PostMapping("/uid/{partyUid}/archive")
    @PreAuthorize("hasAuthority('SUPPLIER.ARCHIVE')")
    public ResponseEntity<Void> archiveSupplier(@PathVariable @ValidUlid String partyUid) {
        service.archiveSupplierByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{partyUid}/activate")
    @PreAuthorize("hasAuthority('SUPPLIER.ARCHIVE')")
    public ResponseEntity<Void> activateSupplier(@PathVariable @ValidUlid String partyUid) {
        service.activateSupplierByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }
}
