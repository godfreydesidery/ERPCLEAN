package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.PettyCashDto;
import com.orbix.engine.modules.pos.domain.dto.PostPettyCashRequestDto;
import com.orbix.engine.modules.pos.service.PettyCashService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Petty-cash payouts from the till (F5.9 / US-POS-014). Gated by {@code POS.PETTY_CASH}. */
@RestController
@RequestMapping("/api/v1/petty-cash")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('POS.PETTY_CASH')")
public class PettyCashController {

    private final PettyCashService service;

    @PostMapping
    public ResponseEntity<PettyCashDto> post(@Valid @RequestBody PostPettyCashRequestDto request) {
        PettyCashDto saved = service.post(request);
        return ResponseEntity.created(URI.create("/api/v1/petty-cash/" + saved.id())).body(saved);
    }

    @GetMapping
    public List<PettyCashDto> listForSession(@RequestParam Long tillSessionId) {
        return service.listForSession(tillSessionId);
    }
}
