package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
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

/**
 * Petty-cash payouts from the till (F5.9 / US-POS-014). Writes stay on
 * {@code POS.PETTY_CASH}; Slice D adds {@code POS.PETTY_CASH.READ} for
 * read-only role assignments. Read endpoints accept either code via
 * {@code hasAnyAuthority}.
 */
@RestController
@RequestMapping("/api/v1/petty-cash")
@RequiredArgsConstructor
public class PettyCashController {

    private final PettyCashService service;

    @PostMapping
    @PreAuthorize("hasAuthority('POS.PETTY_CASH')")
    public ResponseEntity<PettyCashDto> post(@Valid @RequestBody PostPettyCashRequestDto request) {
        PettyCashDto saved = service.post(request);
        return ResponseEntity
            .created(URI.create("/api/v1/petty-cash/uid/" + saved.uid()))
            .body(saved);
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('POS.PETTY_CASH.READ', 'POS.PETTY_CASH')")
    public List<PettyCashDto> listForSession(@RequestParam Long tillSessionId) {
        return service.listForSession(tillSessionId);
    }

    @GetMapping("/uid/{uid}")
    @PreAuthorize("hasAnyAuthority('POS.PETTY_CASH.READ', 'POS.PETTY_CASH')")
    public PettyCashDto getByUid(@PathVariable @ValidUlid String uid) {
        return service.getPettyCashByUid(uid);
    }
}
