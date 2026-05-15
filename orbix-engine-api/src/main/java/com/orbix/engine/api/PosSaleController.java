package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto;
import com.orbix.engine.modules.pos.service.PosSaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** POS sales (F5.2). POST gated by {@code POS.SALE_POST}; reads gated by either POS or admin perms. */
@RestController
@RequestMapping("/api/v1/pos-sales")
@RequiredArgsConstructor
public class PosSaleController {

    private final PosSaleService service;

    @GetMapping
    @PreAuthorize("hasAuthority('POS.MANAGE_TILL') or hasAuthority('POS.SALE_POST')")
    public List<PosSaleDto> list(@RequestParam(required = false) Long branchId,
                                 @RequestParam(required = false) Long tillSessionId) {
        if (tillSessionId != null) {
            return service.listForSession(tillSessionId);
        }
        return service.list(branchId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('POS.MANAGE_TILL') or hasAuthority('POS.SALE_POST')")
    public PosSaleDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('POS.SALE_POST')")
    public ResponseEntity<PosSaleDto> post(@Valid @RequestBody PostPosSaleRequestDto request) {
        PosSaleDto sale = service.post(request);
        return ResponseEntity.created(URI.create("/api/v1/pos-sales/" + sale.id())).body(sale);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('POS.SALE_VOID')")
    public PosSaleDto voidSale(@PathVariable Long id,
                               @Valid @RequestBody VoidPosSaleRequestDto request) {
        return service.voidSale(id, request);
    }
}
