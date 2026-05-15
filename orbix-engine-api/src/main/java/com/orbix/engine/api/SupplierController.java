package com.orbix.engine.api;

import com.orbix.engine.modules.party.domain.dto.CreateSupplierRequestDto;
import com.orbix.engine.modules.party.domain.dto.SupplierResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSupplierRequestDto;
import com.orbix.engine.modules.party.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Supplier management (F1.7). Gated by {@code PARTY.MANAGE_SUPPLIERS}. */
@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_SUPPLIERS')")
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    public List<SupplierResponseDto> listSuppliers() {
        return service.listSuppliers();
    }

    @GetMapping("/{partyId}")
    public SupplierResponseDto getSupplier(@PathVariable Long partyId) {
        return service.getSupplier(partyId);
    }

    @PostMapping
    public ResponseEntity<SupplierResponseDto> createSupplier(
            @Valid @RequestBody CreateSupplierRequestDto request) {
        SupplierResponseDto supplier = service.createSupplier(request);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + supplier.partyId()))
            .body(supplier);
    }

    @PatchMapping("/{partyId}")
    public SupplierResponseDto updateSupplier(@PathVariable Long partyId,
                                              @Valid @RequestBody UpdateSupplierRequestDto request) {
        return service.updateSupplier(partyId, request);
    }

    @PostMapping("/{partyId}/deactivate")
    public ResponseEntity<Void> deactivateSupplier(@PathVariable Long partyId) {
        service.deactivateSupplier(partyId);
        return ResponseEntity.noContent().build();
    }
}
