package com.orbix.engine.api;

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

/** Mid-shift cash pickups (F5.9 / US-POS-013). Gated by {@code POS.CASH_PICKUP}. */
@RestController
@RequestMapping("/api/v1/cash-pickups")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('POS.CASH_PICKUP')")
public class CashPickupController {

    private final CashPickupService service;

    @PostMapping
    public ResponseEntity<CashPickupDto> post(@Valid @RequestBody PostCashPickupRequestDto request) {
        CashPickupDto saved = service.post(request);
        return ResponseEntity.created(URI.create("/api/v1/cash-pickups/" + saved.id())).body(saved);
    }

    @GetMapping
    public List<CashPickupDto> listForSession(@RequestParam Long tillSessionId) {
        return service.listForSession(tillSessionId);
    }
}
