package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.pos.domain.dto.CashPickupDto;
import com.orbix.engine.modules.pos.domain.dto.PostCashPickupRequestDto;
import com.orbix.engine.modules.pos.service.CashPickupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/**
 * Mid-shift cash pickups (F5.9 / US-POS-013). Writes stay on
 * {@code POS.CASH_PICKUP}; Slice D adds {@code POS.CASH_PICKUP.READ} for
 * read-only role assignments. Read endpoints accept either code via
 * {@code hasAnyAuthority}.
 */
@RestController
@RequestMapping("/api/v1/cash-pickups")
@RequiredArgsConstructor
public class CashPickupController {

    private final CashPickupService service;

    @PostMapping
    @PreAuthorize("hasAuthority('POS.CASH_PICKUP')")
    public ResponseEntity<CashPickupDto> post(@Valid @RequestBody PostCashPickupRequestDto request) {
        CashPickupDto saved = service.post(request);
        return ResponseEntity
            .created(URI.create("/api/v1/cash-pickups/uid/" + saved.uid()))
            .body(saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('POS.CASH_PICKUP.READ', 'POS.CASH_PICKUP')")
    public List<CashPickupDto> listForSession(@RequestParam Long tillSessionId) {
        return service.listForSession(tillSessionId);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAnyAuthority('POS.CASH_PICKUP.READ', 'POS.CASH_PICKUP')")
    public CashPickupDto getByUid(@PathVariable @ValidUlid String uid) {
        return service.getCashPickupByUid(uid);
    }
}
